package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.service.MatchFixtureLookupService;
import it.capoldan.fantawin.service.PlayerMatchStatImportService;
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

    public MatchStatsController(PlayerMatchStatImportService playerMatchStatImportService,
                                 MatchFixtureLookupService matchFixtureLookupService) {
        this.playerMatchStatImportService = playerMatchStatImportService;
        this.matchFixtureLookupService = matchFixtureLookupService;
    }

    @PostMapping(value = "/fanta-private/matchstats/import", consumes = "multipart/form-data", produces = "application/json")
    public Mono<ResponseEntity<Object>> importMatchStats(@RequestPart(value = "file", required = true) Flux<Part> file) {
        log.info("Richiesta POST /fanta-private/matchstats/import ricevuta");
        return playerMatchStatImportService.importFromCsv(file)
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta di import statistiche fallita", ex));
    }

    @GetMapping(value = "/fanta-private/matchstats/fixture-lookup/{realTeam}/{matchday}", produces = "application/json")
    public Mono<ResponseEntity<Object>> lookupMatchFixture(@PathVariable("realTeam") String realTeam,
                                                            @PathVariable("matchday") Integer matchday) {
        log.info("Richiesta GET /fanta-private/matchstats/fixture-lookup/{}/{} ricevuta", realTeam, matchday);
        return matchFixtureLookupService.lookupFixture(realTeam, matchday)
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta fixture-lookup fallita per realTeam={} matchday={}", realTeam, matchday, ex));
    }
}
