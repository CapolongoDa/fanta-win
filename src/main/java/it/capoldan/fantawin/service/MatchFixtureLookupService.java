package it.capoldan.fantawin.service;

import it.capoldan.fantawin.exception.ExceptionsCodes;
import it.capoldan.fantawin.exception.NotFoundException;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.model.Match;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.model.MatchesResponse;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.FixtureLookupResult;
import it.capoldan.fantawin.middleware.externalclient.footballdata.FootballDataClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Interroga Football-Data.org (stesso FootballDataClient usato da FootballDataSyncJob) per
 * recuperare il vero avversario/casa-trasferta/data di una squadra reale per una specifica
 * giornata di Serie A, gia' disputata o futura. Pensata come aiuto per compilare correttamente
 * il CSV di /fanta-private/matchstats/import: un errore di trascrizione dell'avversario (es.
 * avversario scritto uguale alla squadra stessa) non viene segnalato da nessun controllo a valle
 * e degrada silenziosamente il Modulo 2 del FantaRating (storico specifico contro l'avversario).
 */
@Slf4j
@Service
public class MatchFixtureLookupService {

    private static final String SERIE_A_COMPETITION_CODE = "SA";
    // Serie A ha 38 giornate a stagione: un limite ampio garantisce di coprire qualunque
    // giornata passata o futura venga richiesta, indipendentemente da quando si effettua la query.
    private static final int MATCH_HISTORY_LIMIT = 40;

    private final FootballDataClient footballDataClient;
    private final FootballDataTeamIdResolver teamIdResolver;

    public MatchFixtureLookupService(FootballDataClient footballDataClient, FootballDataTeamIdResolver teamIdResolver) {
        this.footballDataClient = footballDataClient;
        this.teamIdResolver = teamIdResolver;
    }

    public Mono<FixtureLookupResult> lookupFixture(String realTeam, Integer matchday) {
        log.info("Lookup fixture Football-Data.org per realTeam={} matchday={}", realTeam, matchday);
        return teamIdResolver.resolveTeamIds(Set.of(realTeam))
                .flatMap(resolved -> {
                    Integer teamId = resolved.get(realTeam);
                    if (teamId == null) {
                        log.warn("Impossibile risolvere il teamId Football-Data.org per realTeam={}", realTeam);
                        return Mono.<FixtureLookupResult>error(new NotFoundException(
                                "Squadra '" + realTeam + "' non risolvibile su Football-Data.org",
                                ExceptionsCodes.ERROR_CODE_NOT_FOUND));
                    }
                    return findMatch(teamId, realTeam, matchday);
                })
                .doOnError(ex -> log.warn("Errore nel lookup fixture per realTeam={} matchday={}", realTeam, matchday, ex));
    }

    private Mono<FixtureLookupResult> findMatch(Integer teamId, String realTeam, Integer matchday) {
        Mono<MatchesResponse> finishedMono = footballDataClient.getTeamMatches(teamId, "FINISHED", MATCH_HISTORY_LIMIT);
        Mono<MatchesResponse> scheduledMono = footballDataClient.getTeamMatches(teamId, "SCHEDULED", MATCH_HISTORY_LIMIT);

        return Mono.zip(finishedMono, scheduledMono).flatMap(tuple -> {
            List<Match> allMatches = concat(tuple.getT1(), tuple.getT2());

            Optional<Match> found = allMatches.stream()
                    .filter(m -> SERIE_A_COMPETITION_CODE.equals(Objects.requireNonNull(m.getCompetition()).getCode()))
                    .filter(m -> matchday.equals(m.getMatchday()))
                    .findFirst();

            if (found.isEmpty()) {
                log.warn("Nessuna partita di Serie A trovata per realTeam={} matchday={}", realTeam, matchday);
                return Mono.error(new NotFoundException(
                        "Nessuna partita trovata per '" + realTeam + "' alla giornata " + matchday,
                        ExceptionsCodes.ERROR_CODE_NOT_FOUND));
            }

            Match match = found.get();
            boolean home = Objects.equals(Objects.requireNonNull(match.getHomeTeam()).getId(), teamId);
            String opponent = home
                    ? Objects.requireNonNull(match.getAwayTeam()).getName()
                    : Objects.requireNonNull(match.getHomeTeam()).getName();

            FixtureLookupResult result = FixtureLookupResult.builder()
                    .realTeam(realTeam)
                    .opponentTeam(opponent)
                    .home(home)
                    .matchday(matchday)
                    .utcDate(match.getUtcDate() == null ? null : match.getUtcDate().toString())
                    .status(match.getStatus())
                    .build();

            log.info("Fixture trovata per realTeam={}: avversario={} casa={} giornata={} status={}",
                    realTeam, opponent, home, matchday, match.getStatus());
            return Mono.just(result);
        });
    }

    private List<Match> concat(MatchesResponse a, MatchesResponse b) {
        List<Match> result = new ArrayList<>();
        if (a.getMatches() != null) result.addAll(a.getMatches());
        if (b.getMatches() != null) result.addAll(b.getMatches());
        return result;
    }
}
