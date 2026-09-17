package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.service.MatchFixtureLookupService;
import it.capoldan.fantawin.service.PlayerMatchStatImportService;
import it.capoldan.fantawin.service.RealMatchStatsSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
public class MatchStatsController {

    private final PlayerMatchStatImportService playerMatchStatImportService;
    private final MatchFixtureLookupService matchFixtureLookupService;
    private final RealMatchStatsSyncService realMatchStatsSyncService;

    public MatchStatsController(PlayerMatchStatImportService playerMatchStatImportService,
                                 MatchFixtureLookupService matchFixtureLookupService,
                                 RealMatchStatsSyncService realMatchStatsSyncService) {
        this.playerMatchStatImportService = playerMatchStatImportService;
        this.matchFixtureLookupService = matchFixtureLookupService;
        this.realMatchStatsSyncService = realMatchStatsSyncService;
    }

    @PostMapping(value = "/fanta-private/matchstats/import", consumes = "multipart/form-data", produces = "application/json")
    public Mono<ResponseEntity<Object>> importMatchStats(@RequestPart(value = "file", required = true) Flux<Part> file) {
        log.info("Richiesta POST /fanta-private/matchstats/import ricevuta");
        return playerMatchStatImportService.importFromCsv(file)
                .doOnNext(result -> log.info("Richiesta POST /fanta-private/matchstats/import completata: {} importati, {} scartati",
                        result.getImported(), result.getSkipped()))
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta POST /fanta-private/matchstats/import fallita", ex));
    }

    @GetMapping(value = "/fanta-private/matchstats/fixture-lookup/{realTeam}/{matchday}", produces = "application/json")
    public Mono<ResponseEntity<Object>> lookupMatchFixture(@PathVariable("realTeam") String realTeam,
                                                            @PathVariable("matchday") Integer matchday) {
        log.info("Richiesta GET /fanta-private/matchstats/fixture-lookup/{}/{} ricevuta", realTeam, matchday);
        return matchFixtureLookupService.lookupFixture(realTeam, matchday)
                .doOnNext(result -> log.info("Richiesta GET /fanta-private/matchstats/fixture-lookup/{}/{} completata: avversario={} status={}",
                        realTeam, matchday, result.getOpponentTeam(), result.getStatus()))
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta GET /fanta-private/matchstats/fixture-lookup/{}/{} fallita", realTeam, matchday, ex));
    }

    @PostMapping(value = "/fanta-private/matchstats/sync-real/{rosterId}/{matchday}", produces = "application/json")
    public Mono<ResponseEntity<Object>> syncRealMatchStats(@PathVariable("rosterId") String rosterId,
                                                            @PathVariable("matchday") Integer matchday) {
        log.info("Richiesta POST /fanta-private/matchstats/sync-real/{}/{} ricevuta", rosterId, matchday);
        return realMatchStatsSyncService.syncMatchStats(rosterId, matchday)
                .doOnNext(result -> log.info("Richiesta POST /fanta-private/matchstats/sync-real/{}/{} completata: {} giocatori aggiornati, {} squadre non risolte",
                        rosterId, matchday, result.getUpdatedPlayers().size(), result.getUnresolvedTeams().size()))
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta POST /fanta-private/matchstats/sync-real/{}/{} fallita", rosterId, matchday, ex));
    }
}
