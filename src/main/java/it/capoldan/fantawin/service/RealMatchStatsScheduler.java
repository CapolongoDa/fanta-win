package it.capoldan.fantawin.service;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.model.RoundsResponse;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.model.SmRound;
import it.capoldan.fantawin.middleware.dao.dynamo.RosterDao;
import it.capoldan.fantawin.middleware.externalclient.sportmonks.SportMonksClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Automatizza {@link RealMatchStatsSyncService}: ogni settimana, quando i tabellini del turno del
 * weekend sono ormai definitivi su SportMonks, sincronizza l'ultima giornata segnata come "finished"
 * per ogni rosa esistente (RosterDao.findAll - una sola sincronizzazione periodica copre tutte le leghe
 * dell'utente). Non sostituisce il trigger on-demand su /fanta-private/matchstats/sync-real/{rosterId}/{matchday}
 * (utile per rincorrere manualmente una giornata specifica o riprovare in caso di fallimento).
 */
@Slf4j
@Service
@AllArgsConstructor
public class RealMatchStatsScheduler {

    private final RosterDao rosterDao;
    private final SportMonksClient sportMonksClient;
    private final RealMatchStatsSyncService realMatchStatsSyncService;
    private final FantaWinConfigs fantaWinConfigs;

    // Default: ogni lunedi' alle 6:00, quando le partite del weekend sono ormai concluse e i tabellini
    // definitivi. Configurabile per anticipare/posticipare senza dover ricompilare.
    @Scheduled(cron = "${fantawin.match-stats.sync-cron:0 0 6 * * MON}")
    public void syncLatestFinishedMatchdayForAllRosters() {
        Integer seasonId = fantaWinConfigs.getSportmonksSerieASeasonId();
        if (seasonId == null) {
            log.info("fantawin.sportmonks-serie-a-season-id non configurato: sync automatica statistiche reali saltata");
            return;
        }
        log.info("Avvio sync automatica statistiche reali per tutte le rose");
        // Entry point di uno @Scheduled: nessun consumer reattivo a valle da comporre, il subscribe()
        // qui e' l'eccezione legittima al pattern "mai subscribe manuale nei service" usato altrove.
        sportMonksClient.getRoundsForSeason(seasonId)
                .flatMap(rounds -> {
                    Optional<Integer> latestFinished = latestFinishedMatchday(rounds);
                    if (latestFinished.isEmpty()) {
                        log.info("Nessuna giornata segnata come conclusa su SportMonks: sync automatica saltata");
                        return Mono.empty();
                    }
                    log.info("Giornata piu' recente conclusa su SportMonks: {}", latestFinished.get());
                    return syncAllRosters(latestFinished.get());
                })
                .doOnError(ex -> log.warn("Sync automatica statistiche reali fallita", ex))
                .onErrorResume(ex -> Mono.empty())
                .subscribe();
    }

    private Mono<Void> syncAllRosters(Integer matchday) {
        return rosterDao.findAll()
                .flatMap(roster -> realMatchStatsSyncService.syncMatchStats(roster.getRosterId(), matchday)
                        .doOnSuccess(result -> log.info("Sync automatica completata per rosterId={} matchday={}",
                                roster.getRosterId(), matchday))
                        .onErrorResume(ex -> {
                            log.warn("Sync automatica fallita per rosterId={} matchday={}", roster.getRosterId(), matchday, ex);
                            return Mono.empty();
                        }))
                .then();
    }

    private static Optional<Integer> latestFinishedMatchday(RoundsResponse response) {
        List<SmRound> rounds = response == null || response.getData() == null ? List.of() : response.getData();
        return rounds.stream()
                .filter(r -> Boolean.TRUE.equals(r.getFinished()))
                .map(SmRound::getName)
                .filter(Objects::nonNull)
                .map(RealMatchStatsScheduler::parseMatchdayOrNull)
                .filter(Objects::nonNull)
                .max(Integer::compareTo);
    }

    private static Integer parseMatchdayOrNull(String roundName) {
        try {
            return Integer.valueOf(roundName.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
