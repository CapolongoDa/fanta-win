package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.service.PlayerMatchStatImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class MatchStatsController {

    private final PlayerMatchStatImportService playerMatchStatImportService;

    public MatchStatsController(PlayerMatchStatImportService playerMatchStatImportService) {
        this.playerMatchStatImportService = playerMatchStatImportService;
    }

    @PostMapping(value = "/fanta-private/matchstats/import", consumes = "multipart/form-data", produces = "application/json")
    public Mono<ResponseEntity<Object>> importMatchStats(@RequestPart(value = "file", required = true) Flux<Part> file) {
        return playerMatchStatImportService.importFromCsv(file)
                .map(response -> ResponseEntity.<Object>ok(response));
    }
}
