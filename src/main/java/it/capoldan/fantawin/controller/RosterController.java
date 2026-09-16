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
                .map(ResponseEntity::ok);
    }

    @PostMapping(value = "/fanta-private/addPlayer/{rosterId}", consumes = "application/json", produces = "application/json")
    public Mono<ResponseEntity<Object>> addOrUpdatePlayer(@PathVariable("rosterId") String rosterId,
                                                            @Valid @RequestBody Mono<Player> player) {
        return player.flatMap(p -> rosterService.addOrUpdatePlayer(p, rosterId))
                .map(ResponseEntity::ok);
    }

    @DeleteMapping(value = "/fanta-private/{playerId}/{rosterId}", produces = "application/json")
    public Mono<ResponseEntity<Object>> deletePlayer(@PathVariable("playerId") String playerId,
                                                       @PathVariable("rosterId") String rosterId) {
        return rosterService.deletePlayer(playerId, rosterId)
                .then(Mono.just(new ResponseEntity<>(HttpStatus.NO_CONTENT)));
    }
}
