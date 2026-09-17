package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.generated.openapi.server.v1.dto.BulkAddRosterRequest;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.Player;
import it.capoldan.fantawin.security.AuthenticatedUserProvider;
import it.capoldan.fantawin.service.RosterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestController
@Validated
public class RosterController {

    private final RosterService rosterService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public RosterController(RosterService rosterService, AuthenticatedUserProvider authenticatedUserProvider) {
        this.rosterService = rosterService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @GetMapping(value = "/fanta-private/rosters", produces = "application/json")
    public Mono<ResponseEntity<Object>> listMyRosters() {
        log.info("Richiesta GET /fanta-private/rosters ricevuta");
        return authenticatedUserProvider.currentUserId()
                .flatMap(rosterService::listMyRosters)
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta listMyRosters fallita", ex));
    }

    @GetMapping(value = "/fanta-private/getRoster/{rosterId}", produces = "application/json")
    public Mono<ResponseEntity<Object>> getRoster(@PathVariable("rosterId") String rosterId,
                                                   @NotNull @RequestParam(value = "matchDay", required = true) Integer matchDay) {
        log.info("Richiesta GET /fanta-private/getRoster/{} matchDay={} ricevuta", rosterId, matchDay);
        return authenticatedUserProvider.currentUserId()
                .flatMap(callerId -> rosterService.getRoster(matchDay, rosterId, callerId))
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta getRoster fallita per rosterId={}", rosterId, ex));
    }

    @PostMapping(value = "/fanta-private/addPlayer/{rosterId}", consumes = "application/json", produces = "application/json")
    public Mono<ResponseEntity<Object>> addOrUpdatePlayer(@PathVariable("rosterId") String rosterId,
                                                            @Valid @RequestBody Mono<Player> player) {
        log.info("Richiesta POST /fanta-private/addPlayer/{} ricevuta", rosterId);
        return player.zipWith(authenticatedUserProvider.currentUserId())
                .flatMap(tuple -> rosterService.addOrUpdatePlayer(tuple.getT1(), rosterId, tuple.getT2()))
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta addOrUpdatePlayer fallita per rosterId={}", rosterId, ex));
    }

    @PostMapping(value = "/fanta-private/addPlayers", consumes = "application/json", produces = "application/json")
    public Mono<ResponseEntity<Object>> addPlayersBulk(@Valid @RequestBody Mono<BulkAddRosterRequest> request) {
        log.info("Richiesta POST /fanta-private/addPlayers ricevuta");
        return request.zipWith(authenticatedUserProvider.currentUserId())
                .flatMap(tuple -> rosterService.addPlayersBulk(
                        tuple.getT1().getPlayers(), tuple.getT1().getRosterId(), tuple.getT2()))
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta addPlayersBulk fallita", ex));
    }

    @DeleteMapping(value = "/fanta-private/{playerId}/{rosterId}", produces = "application/json")
    public Mono<ResponseEntity<Object>> deletePlayer(@PathVariable("playerId") String playerId,
                                                       @PathVariable("rosterId") String rosterId) {
        log.info("Richiesta DELETE /fanta-private/{}/{} ricevuta", playerId, rosterId);
        return authenticatedUserProvider.currentUserId()
                .flatMap(callerId -> rosterService.deletePlayer(playerId, rosterId, callerId))
                .then(Mono.just(new ResponseEntity<>(HttpStatus.NO_CONTENT)))
                .doOnError(ex -> log.warn("Richiesta deletePlayer fallita per playerId={} rosterId={}", playerId, rosterId, ex));
    }
}
