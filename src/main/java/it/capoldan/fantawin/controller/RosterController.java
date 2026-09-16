package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.generated.openapi.server.v1.dto.Player;
import it.capoldan.fantawin.service.RosterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Non implementa piu' l'interfaccia generata RosterApi: i path/parametri sono ridichiarati qui a
 * mano (devono restare allineati con docs/openapi/fanta-win-api-internal.yaml) per poter
 * restituire Mono&lt;ResponseEntity&lt;Object&gt;&gt; invece del tipo di risposta puntuale che
 * l'interfaccia generata imponeva per ogni operazione. Il corpo effettivo resta comunque il DTO
 * tipizzato del servizio (Jackson serializza in base al tipo a runtime, non al generic Object a
 * compile time): il payload JSON prodotto e' identico a prima.
 *
 * Prezzo di questa scelta: si perde la garanzia "il controller non compila se non rispetta lo
 * spec" che dava l'interfaccia generata, e la documentazione Swagger/OpenAPI live (springdoc)
 * vedra' uno schema di risposta generico invece di quello puntuale, salvo aggiungere @Operation/
 * @ApiResponse espliciti.
 */
@RestController
@Validated
public class RosterController {

    private final RosterService rosterService;

    public RosterController(RosterService rosterService) {
        this.rosterService = rosterService;
    }

    @GetMapping(value = "/fanta-private/getRoster/{rosterId}", produces = "application/json")
    public Mono<ResponseEntity<Object>> getRoster(@PathVariable("rosterId") String rosterId,
                                                   @NotNull @RequestParam(value = "matchDay", required = true) Integer matchDay) {
        return rosterService.getRoster(matchDay, rosterId)
                .map(response -> ResponseEntity.<Object>ok(response));
    }

    @PostMapping(value = "/fanta-private/addPlayer/{rosterId}", consumes = "application/json", produces = "application/json")
    public Mono<ResponseEntity<Object>> addOrUpdatePlayer(@PathVariable("rosterId") String rosterId,
                                                            @Valid @RequestBody Mono<Player> player) {
        return player.flatMap(p -> rosterService.addOrUpdatePlayer(p, rosterId))
                .map(response -> ResponseEntity.<Object>ok(response));
    }

    @DeleteMapping(value = "/fanta-private/{playerId}/{rosterId}", produces = "application/json")
    public Mono<ResponseEntity<Object>> deletePlayer(@PathVariable("playerId") String playerId,
                                                       @PathVariable("rosterId") String rosterId) {
        return rosterService.deletePlayer(playerId, rosterId)
                .then(Mono.just(new ResponseEntity<Object>(HttpStatus.NO_CONTENT)));
    }
}
