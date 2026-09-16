package it.capoldan.fantawin.service;

import it.capoldan.fantawin.config.FootballDataTeamsConfig;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.model.CompetitionTeam;
import it.capoldan.fantawin.middleware.externalclient.footballdata.FootballDataClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Risolve i teamId numerici di Football-Data.org per un insieme di squadre reali (per nome),
 * incrociandole contro GET /competitions/SA/teams, invece di richiedere che vengano scoperti a
 * mano e incollati in football-data.team-ids.*: un teamId sbagliato inserito manualmente farebbe
 * leggere silenziosamente i dati di un'altra squadra (nessun errore, nessun avviso visibile),
 * mentre un nome non risolvibile fallisce in modo esplicito nei log.
 *
 * football-data.team-ids.* resta disponibile come override manuale per singola squadra (utile se
 * il nome usato nell'anagrafica giocatori non matcha nome/shortName/tla di Football-Data.org): se
 * valorizzato per una squadra, quell'id ha sempre la precedenza e la risoluzione automatica non
 * viene nemmeno interpellata per quella squadra.
 */
@Slf4j
@Service
public class FootballDataTeamIdResolver {

    private static final String SERIE_A_COMPETITION_CODE = "SA";

    private final FootballDataClient footballDataClient;
    private final FootballDataTeamsConfig teamsConfig;

    // Cache in-memory per la durata del processo: la mappatura squadra Serie A -> teamId cambia
    // raramente nell'arco di una stagione, non serve richiamare l'API a ogni sync giornaliera.
    private final AtomicReference<Map<String, Integer>> serieATeamIdsByNormalizedName = new AtomicReference<>();

    public FootballDataTeamIdResolver(FootballDataClient footballDataClient, FootballDataTeamsConfig teamsConfig) {
        this.footballDataClient = footballDataClient;
        this.teamsConfig = teamsConfig;
    }

    /**
     * Per ogni nome di squadra reale in {@code realTeamNames} restituisce il teamId da usare:
     * l'override manuale da football-data.team-ids.* se presente, altrimenti quello risolto
     * automaticamente per nome. Le squadre non risolvibili (ne' in override ne' trovate per nome
     * su Football-Data.org) vengono escluse dal risultato e segnalate con un warning, cosi' la
     * sync delle altre squadre puo' proseguire.
     */
    public Mono<Map<String, Integer>> resolveTeamIds(Set<String> realTeamNames) {
        log.info("Risoluzione teamId Football-Data.org per {} squadre reali", realTeamNames.size());
        if (realTeamNames.isEmpty()) {
            return Mono.just(Map.of());
        }

        Map<String, Integer> manualOverrides = teamsConfig.getTeamIds();
        boolean allOverridden = realTeamNames.stream().allMatch(manualOverrides::containsKey);
        if (allOverridden) {
            Map<String, Integer> result = new HashMap<>();
            realTeamNames.forEach(name -> result.put(name, manualOverrides.get(name)));
            return Mono.just(result);
        }

        return fetchSerieATeamIdsByName().map(byName -> {
            Map<String, Integer> resolved = new HashMap<>();
            for (String realTeam : realTeamNames) {
                Integer override = manualOverrides.get(realTeam);
                if (override != null) {
                    resolved.put(realTeam, override);
                    continue;
                }
                Integer autoResolved = byName.get(normalize(realTeam));
                if (autoResolved != null) {
                    log.info("teamId per '{}' risolto automaticamente da Football-Data.org: {} " +
                                    "(per evitare la risoluzione ad ogni avvio puoi valorizzarlo a mano con " +
                                    "football-data.team-ids.{}={})",
                            realTeam, autoResolved, realTeam, autoResolved);
                    resolved.put(realTeam, autoResolved);
                } else {
                    log.warn("Impossibile risolvere il teamId per '{}': nessuna squadra di Serie A corrisponde " +
                                    "a questo nome su Football-Data.org. Verifica il nome usato nell'anagrafica " +
                                    "giocatori oppure valorizza l'id a mano con football-data.team-ids.{}=<id>.",
                            realTeam, realTeam);
                }
            }
            return resolved;
        });
    }

    private Mono<Map<String, Integer>> fetchSerieATeamIdsByName() {
        Map<String, Integer> cached = serieATeamIdsByNormalizedName.get();
        if (cached != null) {
            return Mono.just(cached);
        }
        return footballDataClient.getCompetitionTeams(SERIE_A_COMPETITION_CODE)
                .map(response -> {
                    Map<String, Integer> byName = new HashMap<>();
                    if (response.getTeams() != null) {
                        for (CompetitionTeam team : response.getTeams()) {
                            if (team.getId() == null) continue;
                            putIfPresent(byName, team.getName(), team.getId());
                            putIfPresent(byName, team.getShortName(), team.getId());
                            putIfPresent(byName, team.getTla(), team.getId());
                        }
                    }
                    serieATeamIdsByNormalizedName.set(byName);
                    return byName;
                });
    }

    private static void putIfPresent(Map<String, Integer> map, String name, Integer id) {
        if (name != null && !name.isBlank()) {
            map.put(normalize(name), id);
        }
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String noAccents = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase().replaceAll("\\s+", " ");
    }
}
