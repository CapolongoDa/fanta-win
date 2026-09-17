package it.capoldan.fantawin.service;

import it.capoldan.fantawin.config.SportMonksTeamsConfig;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.model.SmTeam;
import it.capoldan.fantawin.middleware.externalclient.sportmonks.SportMonksClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Risolve il teamId numerico di SportMonks per una squadra reale (per nome), tramite
 * GET /teams/search/{nome}: prende il primo risultato restituito (il piu' pertinente secondo la
 * ricerca di SportMonks), a meno che non sia stato configurato un override manuale in
 * sportmonks.team-ids.* (che ha sempre la precedenza).
 *
 * A differenza di FootballDataTeamIdResolver (che scarica l'intero elenco squadre di Serie A e
 * fa il match in locale), qui la ricerca e' gia' filtrata lato SportMonks: non serve un elenco
 * completo ne' un normalizzatore di accenti/maiuscole per il matching.
 */
@Slf4j
@Service
public class SportMonksTeamIdResolver {

    private final SportMonksClient sportMonksClient;
    private final SportMonksTeamsConfig teamsConfig;

    // Cache in-memory per la durata del processo: la mappatura squadra -> teamId non cambia
    // nell'arco di una stagione, non serve richiamare l'API di ricerca ad ogni sync.
    private final Map<String, Integer> resolvedTeamIdByRealTeam = new ConcurrentHashMap<>();

    public SportMonksTeamIdResolver(SportMonksClient sportMonksClient, SportMonksTeamsConfig teamsConfig) {
        this.sportMonksClient = sportMonksClient;
        this.teamsConfig = teamsConfig;
    }

    /** Restituisce il teamId per {@code realTeam}, oppure vuoto se non risolvibile. */
    public Mono<Optional<Integer>> resolveTeamId(String realTeam) {
        Integer override = teamsConfig.getTeamIds().get(realTeam);
        if (override != null) {
            return Mono.just(Optional.of(override));
        }
        Integer cached = resolvedTeamIdByRealTeam.get(realTeam);
        if (cached != null) {
            return Mono.just(Optional.of(cached));
        }

        log.info("Risoluzione teamId SportMonks per realTeam={}", realTeam);
        return sportMonksClient.searchTeams(realTeam)
                .map(response -> firstTeamId(response == null ? null : response.getData()))
                .doOnNext(resolved -> resolved.ifPresentOrElse(
                        teamId -> {
                            resolvedTeamIdByRealTeam.put(realTeam, teamId);
                            log.info("teamId SportMonks per '{}' risolto automaticamente: {} " +
                                            "(per evitare la ricerca ad ogni sync puoi valorizzarlo a mano con " +
                                            "sportmonks.team-ids.{}={})",
                                    realTeam, teamId, realTeam, teamId);
                        },
                        () -> log.warn("Impossibile risolvere il teamId SportMonks per '{}': nessun risultato dalla " +
                                        "ricerca per nome. Verifica il nome usato nell'anagrafica giocatori oppure " +
                                        "valorizza l'id a mano con sportmonks.team-ids.{}=<id>.",
                                realTeam, realTeam)));
    }

    private static Optional<Integer> firstTeamId(List<SmTeam> teams) {
        if (teams == null || teams.isEmpty()) {
            return Optional.empty();
        }
        SmTeam first = teams.get(0);
        return first.getId() == null ? Optional.empty() : Optional.of(first.getId());
    }
}
