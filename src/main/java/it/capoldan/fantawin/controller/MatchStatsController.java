package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.generated.openapi.server.v1.api.MatchStatsApi;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.MatchStatsImportResult;
import it.capoldan.fantawin.service.PlayerMatchStatImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class MatchStatsController implements MatchStatsApi {

    private final PlayerMatchStatImportService playerMatchStatImportService;

    public MatchStatsController(PlayerMatchStatImportService playerMatchStatImportService) {
        this.playerMatchStatImportService = playerMatchStatImportService;
    }

    @Override
    public Mono<ResponseEntity<MatchStatsImportResult>> importMatchStats(Flux<Part> file, ServerWebExchange exchange) {
        return playerMatchStatImportService.importFromCsv(file).map(ResponseEntity::ok);
    }
}
