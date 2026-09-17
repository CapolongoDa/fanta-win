package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.dto.PlayerCatalogDto;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.AddPlayerCatalogEntryRequest;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.PlayerCatalogEntry;
import it.capoldan.fantawin.service.PlayerCatalogImportService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping(value = "/fanta-private/playercatalog", consumes = "application/json", produces = "application/json")
    public Mono<ResponseEntity<Object>> addPlayerCatalogEntry(@Valid @RequestBody Mono<AddPlayerCatalogEntryRequest> request) {
        log.info("Richiesta POST /fanta-private/playercatalog ricevuta");
        return request.flatMap(req -> playerCatalogImportService.addPlayer(req.getNome(), req.getRuolo(), req.getSquadra()))
                .map(PlayerCatalogController::toApiEntry)
                .doOnNext(saved -> log.info("Richiesta POST /fanta-private/playercatalog completata: catalogId={} creato", saved.getCatalogId()))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body((Object) response))
                .doOnError(ex -> log.warn("Richiesta POST /fanta-private/playercatalog fallita", ex));
    }

    private static PlayerCatalogEntry toApiEntry(PlayerCatalogDto dto) {
        return PlayerCatalogEntry.builder()
                .catalogId(dto.getCatalogId())
                .nome(dto.getNome())
                .ruolo(dto.getRuolo().name())
                .squadra(dto.getSquadra())
                .build();
    }
}
