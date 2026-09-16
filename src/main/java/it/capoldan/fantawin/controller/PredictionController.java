package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.generated.openapi.server.v1.dto.LineupRequest;
import it.capoldan.fantawin.service.LineupPredictionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Non implementa piu' l'interfaccia generata PredictionApi: vedi il commento su RosterController
 * per il motivo (Mono&lt;ResponseEntity&lt;Object&gt;&gt; invece del tipo puntuale generato) e il
 * relativo trade-off (nessun controllo automatico di conformita' allo spec a compile time).
 */
@RestController
@Validated
public class PredictionController {

    private final LineupPredictionService lineupPredictionService;

    public PredictionController(LineupPredictionService lineupPredictionService) {
        this.lineupPredictionService = lineupPredictionService;
    }

    @PostMapping(value = "/fanta-private/predict/lineup/{rosterId}", consumes = "application/json", produces = "application/json")
    public Mono<ResponseEntity<Object>> calculateOptimalLineup(@PathVariable("rosterId") String rosterId,
                                                                 @Valid @RequestBody Mono<LineupRequest> lineupRequest) {
        return lineupRequest.flatMap(req -> lineupPredictionService.calculateOptimalLineup(req, rosterId))
                .map(response -> ResponseEntity.<Object>ok(response));
    }
}
