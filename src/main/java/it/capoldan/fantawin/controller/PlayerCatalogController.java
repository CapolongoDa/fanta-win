package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.service.PlayerCatalogImportService;
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
public class PlayerCatalogController {

    private final PlayerCatalogImportService playerCatalogImportService;

    public PlayerCatalogController(PlayerCatalogImportService playerCatalogImportService) {
        this.playerCatalogImportService = playerCatalogImportService;
    }

    @PostMapping(value = "/fanta-private/playercatalog/import", consumes = "multipart/form-data", produces = "application/json")
    public Mono<ResponseEntity<Object>> importPlayerCatalog(@RequestPart(value = "file", required = true) Flux<Part> file) {
        log.info("Richiesta POST /fanta-private/playercatalog/import ricevuta");
        return playerCatalogImportService.importFromCsv(file)
                .doOnNext(result -> log.info("Richiesta POST /fanta-private/playercatalog/import completata: {} importati, {} scartati",
                        result.getImported(), result.getSkipped()))
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta POST /fanta-private/playercatalog/import fallita", ex));
    }
}
