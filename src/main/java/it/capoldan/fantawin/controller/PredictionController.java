package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.generated.openapi.server.v1.api.PredictionApi;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.LineupRequest;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.LineupResponse;
import it.capoldan.fantawin.service.LineupPredictionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class PredictionController implements PredictionApi {

    private final LineupPredictionService lineupPredictionService;

    public PredictionController(LineupPredictionService lineupPredictionService) {
        this.lineupPredictionService = lineupPredictionService;
    }

    @Override
    public Mono<ResponseEntity<LineupResponse>> calculateOptimalLineup(String rosterId,
                                                                       Mono<LineupRequest> lineupRequest,
                                                                       ServerWebExchange exchange) {
        return lineupRequest.flatMap(req -> lineupPredictionService.calculateOptimalLineup(req, rosterId))
                .map(ResponseEntity::ok);
    }
}