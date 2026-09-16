package it.capoldan.fantawin.service;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.dto.FixtureDto;
import it.capoldan.fantawin.dto.PlayerDto;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.model.Match;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.model.MatchesResponse;
import it.capoldan.fantawin.middleware.dao.dynamo.FixtureDao;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerDao;
import it.capoldan.fantawin.middleware.externalclient.footballdata.FootballDataClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import reactor.util.function.Tuples;

/**
 * Sincronizza periodicamente FixturesTable con i dati reali di Football-Data.org.
 * Una sola chiamata (x2: FINISHED + SCHEDULED) per squadra reale, MAI per giocatore.
 */
@Slf4j
@Service
@AllArgsConstructor
public class FootballDataSyncJob {

    private static final String SERIE_A_COMPETITION_CODE = "SA";
    private static final Duration TURNOVER_WINDOW = Duration.ofDays(3);

    // Piano free di Football-Data.org: 10 richieste/minuto (confermato dalla documentazione ufficiale,
    // https://www.football-data.org/documentation/api). Un margine di sicurezza sopra i 6s teorici
    // (60s/10) evita di sforare per via di jitter di rete/scheduling.
    private static final Duration FOOTBALL_DATA_MIN_CALL_INTERVAL = Duration.ofMillis(6500);

    // ASSUNZIONE: codici competizioni europee da confermare contro le risposte reali dell'API
    // (il piano free di football-data.org potrebbe non coprire la Conference League).
    private static final Set<String> EUROPEAN_COMPETITION_CODES = Set.of("CL", "EL", "UECL");

    private final FootballDataClient footballDataClient;
    private final FootballDataTeamIdResolver teamIdResolver;
    private final PlayerDao playerDao;
    private final FixtureDao fixtureDao;

    private final FantaWinConfigs fantaWinConfigs;


    @Scheduled(cron = "${fantawin.football-data.sync-cron:0 0 6 * * *}") // default: ogni giorno alle 6:00
    public void syncAllTeams() {
        log.info("Avvio sincronizzazione Football-Data.org (squadre derivate dall'anagrafica giocatori)");
        // Le squadre da sincronizzare sono derivate dall'anagrafica giocatori (realTeam distinti),
        // non da una whitelist statica in football-data.team-ids.*: quella proprieta' richiedeva di
        // scoprire e incollare a mano i teamId (con football-data.team-ids.Inter= vuoto, Spring scarta
        // la entry dal binding, quindi la mappa risultava vuota e la sync non faceva letteralmente
        // nulla, senza errori). I teamId vengono risolti per nome via teamIdResolver, con
        // football-data.team-ids.* utilizzabile come override manuale per singola squadra.
        playerDao.findAll()
                .map(PlayerDto::getRealTeam)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet())
                .flatMap(teamIdResolver::resolveTeamIds)
                .doOnNext(teamIds -> log.info("Squadre risolte per la sincronizzazione: {}", teamIds.keySet()))
                .flatMapMany(teamIds -> Flux.fromIterable(teamIds.entrySet()))
                .concatMap(entry -> syncTeam(entry.getKey(), entry.getValue())
                        // un fallimento su una squadra non deve bloccare le altre
                        .onErrorResume(ex -> {
                            log.error("Sync fallita per squadra={}", entry.getKey(), ex);
                            return Mono.empty();
                        })
                        // spaziatura anche tra l'ultima chiamata di una squadra e la prima della successiva
                        .delayElement(FOOTBALL_DATA_MIN_CALL_INTERVAL))
                .blockLast();
        log.info("Sincronizzazione Football-Data.org completata");
    }

    private Mono<FixtureDto> syncTeam(String realTeamName, Integer teamId) {
        // Le due chiamate (FINISHED + SCHEDULED) vengono serializzate con una pausa tra l'una e l'altra:
        // erano concorrenti (Mono.zip), il che sforava il rate limit del piano free (10 richieste/minuto)
        // non appena piu' di una squadra veniva sincronizzata nella stessa finestra.
        Mono<MatchesResponse> recentMono = footballDataClient.getTeamMatches(teamId, "FINISHED", 5);

        return recentMono.flatMap(recent ->
                Mono.delay(FOOTBALL_DATA_MIN_CALL_INTERVAL)
                        .then(footballDataClient.getTeamMatches(teamId, "SCHEDULED", 5))
                        .map(upcoming -> Tuples.of(recent, upcoming))
        ).flatMap(tuple -> {
            List<Match> allMatches = concat(tuple.getT1(), tuple.getT2());

            Match nextSerieA = allMatches.stream()
                    .filter(m -> SERIE_A_COMPETITION_CODE.equals(Objects.requireNonNull(m.getCompetition()).getCode()))
                    .filter(m -> "SCHEDULED".equals(m.getStatus()))
                    .min(Comparator.comparing(a -> Objects.requireNonNull(a.getUtcDate())))
                    .orElse(null);

            if (nextSerieA == null) {
                log.warn("Nessuna prossima partita di Serie A trovata per {}", realTeamName);
                return Mono.empty();
            }

            boolean home = Objects.equals(Objects.requireNonNull(nextSerieA.getHomeTeam()).getId(), teamId);
            String opponent = home ? Objects.requireNonNull(nextSerieA.getAwayTeam()).getName() : nextSerieA.getHomeTeam().getName();
            Instant referenceDate = nextSerieA.getUtcDate();

            boolean europeanBefore = hasMatchInWindow(allMatches, OffsetDateTime.from(referenceDate), true, EUROPEAN_COMPETITION_CODES::contains);
            boolean europeanAfter = hasMatchInWindow(allMatches, OffsetDateTime.from(referenceDate), false, EUROPEAN_COMPETITION_CODES::contains);
            // "turno infrasettimanale" = qualsiasi altra partita (non quella di Serie A di riferimento)
            // nella finestra, indipendentemente dalla competizione - vedi nota sotto.
            boolean midweekBefore = hasMatchInWindow(allMatches, OffsetDateTime.from(referenceDate), true, code -> true);
            boolean midweekAfter = hasMatchInWindow(allMatches, OffsetDateTime.from(referenceDate), false, code -> true);

            FixtureDto dto = FixtureDto.builder()
                    .matchDay(nextSerieA.getMatchday())
                    .realTeam(realTeamName)
                    .opponentTeam(opponent)
                    .home(home)
                    // TODO: il vero Match Difficulty Index richiede dati di classifica
                    // (endpoint standings, non presente nello spec attuale). Placeholder neutro
                    // finché non si aggiunge quella fonte dati.
                    .matchDifficulty(fantaWinConfigs.getDefaultMatchDifficulty())
                    .europeanCupBefore(europeanBefore)
                    .europeanCupAfter(europeanAfter)
                    .midweekRoundBefore(midweekBefore)
                    .midweekRoundAfter(midweekAfter)
                    .build();

            return fixtureDao.save(dto)
                    .doOnSuccess(saved -> log.info("Fixture sincronizzata per {}: prossimo avversario {} (matchday {})",
                            realTeamName, opponent, nextSerieA.getMatchday()));
        });
    }

    private boolean hasMatchInWindow(List<Match> matches, OffsetDateTime reference,
                                     boolean before, java.util.function.Predicate<String> competitionFilter) {
        return matches.stream().anyMatch(m -> {
            if (Objects.requireNonNull(m.getUtcDate()).equals(reference)) return false; // esclude la partita di riferimento stessa
            boolean inWindow = before
                    ? m.getUtcDate().isBefore(reference.toInstant()) && Duration.between(m.getUtcDate(), reference).compareTo(TURNOVER_WINDOW) <= 0
                    : m.getUtcDate().isAfter(reference.toInstant()) && Duration.between(reference, m.getUtcDate()).compareTo(TURNOVER_WINDOW) <= 0;
            return inWindow && competitionFilter.test(Objects.requireNonNull(m.getCompetition()).getCode());
        });
    }

    private List<Match> concat(MatchesResponse a, MatchesResponse b) {
        List<Match> result = new java.util.ArrayList<>();
        if (a.getMatches() != null) result.addAll(a.getMatches());
        if (b.getMatches() != null) result.addAll(b.getMatches());
        return result;
    }
}