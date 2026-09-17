package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.service.OfficialLineupSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
public class LineupSyncController {

    private final OfficialLineupSyncService officialLineupSyncService;

    public LineupSyncController(OfficialLineupSyncService officialLineupSyncService) {
        this.officialLineupSyncService = officialLineupSyncService;
    }

    @PostMapping(value = "/fanta-private/lineups/sync-official/{rosterId}", produces = "application/json")
    public Mono<ResponseEntity<Object>> syncOfficialLineup(@PathVariable("rosterId") String rosterId) {
        log.info("Richiesta POST /fanta-private/lineups/sync-official/{} ricevuta", rosterId);
        return officialLineupSyncService.syncOfficialLineup(rosterId)
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta di sync formazione ufficiale fallita per rosterId={}", rosterId, ex));
    }
}
