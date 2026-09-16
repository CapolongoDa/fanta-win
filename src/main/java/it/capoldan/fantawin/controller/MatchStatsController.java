package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.service.PlayerMatchStatImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Non implementa piu' l'interfaccia generata MatchStatsApi: vedi il commento su RosterController
 * per il motivo (Mono&lt;ResponseEntity&lt;Object&gt;&gt; invece del tipo puntuale generato) e il
 * relativo trade-off (nessun controllo automatico di conformita' allo spec a compile time).
 */
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
