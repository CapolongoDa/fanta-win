package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.generated.openapi.server.v1.dto.AddOrUpdatePlayerRequest;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.BulkAddRosterRequest;
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
                .doOnNext(rosters -> log.info("Richiesta GET /fanta-private/rosters completata: {} rose trovate", rosters.size()))
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta GET /fanta-private/rosters fallita", ex));
    }

    @GetMapping(value = "/fanta-private/getRoster/{rosterId}", produces = "application/json")
    public Mono<ResponseEntity<Object>> getRoster(@PathVariable("rosterId") String rosterId,
                                                   @NotNull @RequestParam(value = "matchDay", required = true) Integer matchDay) {
        log.info("Richiesta GET /fanta-private/getRoster/{} matchDay={} ricevuta", rosterId, matchDay);
        return authenticatedUserProvider.currentUserId()
                .flatMap(callerId -> rosterService.getRoster(matchDay, rosterId, callerId))
                .doOnNext(response -> log.info("Richiesta GET /fanta-private/getRoster/{} matchDay={} completata: teamName={}",
                        rosterId, matchDay, response.getTeamName()))
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta GET /fanta-private/getRoster/{} matchDay={} fallita", rosterId, matchDay, ex));
    }

    @PostMapping(value = "/fanta-private/addPlayer/{rosterId}", consumes = "application/json", produces = "application/json")
    public Mono<ResponseEntity<Object>> addOrUpdatePlayer(@PathVariable("rosterId") String rosterId,
                                                            @RequestParam(value = "teamName", required = false) String teamName,
                                                            @Valid @RequestBody Mono<AddOrUpdatePlayerRequest> player) {
        log.info("Richiesta POST /fanta-private/addPlayer/{} teamName={} ricevuta", rosterId, teamName);
        return player.zipWith(authenticatedUserProvider.currentUserId())
                .flatMap(tuple -> rosterService.addOrUpdatePlayer(tuple.getT1(), rosterId, teamName, tuple.getT2()))
                .doOnNext(saved -> log.info("Richiesta POST /fanta-private/addPlayer/{} completata: giocatore id={} salvato", rosterId, saved.getId()))
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta POST /fanta-private/addPlayer/{} fallita", rosterId, ex));
    }

    @PostMapping(value = "/fanta-private/addPlayers", consumes = "application/json", produces = "application/json")
    public Mono<ResponseEntity<Object>> addPlayersBulk(@Valid @RequestBody Mono<BulkAddRosterRequest> request) {
        log.info("Richiesta POST /fanta-private/addPlayers ricevuta");
        return request.zipWith(authenticatedUserProvider.currentUserId())
                .flatMap(tuple -> rosterService.addPlayersBulk(
                        tuple.getT1().getPlayers(), tuple.getT1().getRosterId(), tuple.getT1().getTeamName(), tuple.getT2()))
                .doOnNext(result -> log.info("Richiesta POST /fanta-private/addPlayers completata: {} aggiunti, {} non risolti",
                        result.getAdded().size(), result.getUnresolved().size()))
                .map(response -> ResponseEntity.<Object>ok(response))
                .doOnError(ex -> log.warn("Richiesta POST /fanta-private/addPlayers fallita", ex));
    }

    @DeleteMapping(value = "/fanta-private/{playerId}/{rosterId}", produces = "application/json")
    public Mono<ResponseEntity<Object>> deletePlayer(@PathVariable("playerId") String playerId,
                                                       @PathVariable("rosterId") String rosterId) {
        log.info("Richiesta DELETE /fanta-private/{}/{} ricevuta", playerId, rosterId);
        return authenticatedUserProvider.currentUserId()
                .flatMap(callerId -> rosterService.deletePlayer(playerId, rosterId, callerId))
                .doOnSuccess(v -> log.info("Richiesta DELETE /fanta-private/{}/{} completata", playerId, rosterId))
                .then(Mono.just(new ResponseEntity<>(HttpStatus.NO_CONTENT)))
                .doOnError(ex -> log.warn("Richiesta DELETE /fanta-private/{}/{} fallita", playerId, rosterId, ex));
    }

    @DeleteMapping(value = "/fanta-private/rosters/{rosterId}", produces = "application/json")
    public Mono<ResponseEntity<Object>> deleteRoster(@PathVariable("rosterId") String rosterId) {
        log.info("Richiesta DELETE /fanta-private/rosters/{} ricevuta", rosterId);
        return authenticatedUserProvider.currentUserId()
                .flatMap(callerId -> rosterService.deleteRoster(rosterId, callerId))
                .doOnSuccess(v -> log.info("Richiesta DELETE /fanta-private/rosters/{} completata", rosterId))
                .then(Mono.just(new ResponseEntity<>(HttpStatus.NO_CONTENT)))
                .doOnError(ex -> log.warn("Richiesta DELETE /fanta-private/rosters/{} fallita", rosterId, ex));
    }
}
