package it.capoldan.fantawin.service;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.config.FootballDataTeamsConfig;
import it.capoldan.fantawin.dto.FixtureDto;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.model.Match;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.model.MatchesResponse;
import it.capoldan.fantawin.middleware.dao.dynamo.FixtureDao;
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

    // ASSUNZIONE: codici competizioni europee da confermare contro le risposte reali dell'API
    // (il piano free di football-data.org potrebbe non coprire la Conference League).
    private static final Set<String> EUROPEAN_COMPETITION_CODES = Set.of("CL", "EL", "UECL");

    private final FootballDataClient footballDataClient;
    private final FootballDataTeamsConfig teamsConfig;
    private final FixtureDao fixtureDao;

    private final FantaWinConfigs fantaWinConfigs;


    @Scheduled(cron = "${fantawin.football-data.sync-cron:0 0 6 * * *}") // default: ogni giorno alle 6:00
    public void syncAllTeams() {
        Flux.fromIterable(teamsConfig.getTeamIds().entrySet())
                .concatMap(entry -> syncTeam(entry.getKey(), entry.getValue())
                        // un fallimento su una squadra non deve bloccare le altre
                        .onErrorResume(ex -> {
                            log.error("Sync fallita per squadra={}", entry.getKey(), ex);
                            return Mono.empty();
                        })
                        .delayElement(Duration.ofMillis(1500))) // rispetta il rate limit del piano free
                .blockLast();
    }

    private Mono<FixtureDto> syncTeam(String realTeamName, Integer teamId) {
        Mono<MatchesResponse> recentMono = footballDataClient.getTeamMatches(teamId, "FINISHED", 5);
        Mono<MatchesResponse> upcomingMono = footballDataClient.getTeamMatches(teamId, "SCHEDULED", 5);

        return Mono.zip(recentMono, upcomingMono).flatMap(tuple -> {
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

            return fixtureDao.save(dto);
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