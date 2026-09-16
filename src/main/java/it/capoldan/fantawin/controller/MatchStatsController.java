package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.service.PlayerMatchStatImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
public class MatchStatsController {

    private final PlayerMatchStatImportService playerMatchStatImportService;

    public MatchStatsController(PlayerMatchStatImportService playerMatchStatImportService) {
        this.playerMatchStatImportService = playerMatchStatImportService;
    }

    @PostMapping(value = "/fanta-private/matchstats/import", consumes = "multipart/form-data", produces = "application/json")
    public Mono<ResponseEntity<Object>> importMatchStats(@RequestPart(value = "file", required = true) Flux<Part> file) {
        log.info("Richiesta POST /fanta-private/matchstats/import ricevuta");
        return playerMatchStatImportService.importFromCsv(file)
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta di import statistiche fallita", ex));
    }
}
