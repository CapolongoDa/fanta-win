package it.capoldan.fantawin.controller;

import it.capoldan.fantawin.generated.openapi.server.v1.api.RosterApi;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.Player;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.RosterResponse;
import it.capoldan.fantawin.service.RosterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class RosterController implements RosterApi {

    private final RosterService rosterService;

    public RosterController(RosterService rosterService) {
        this.rosterService = rosterService;
    }

    @Override
    public Mono<ResponseEntity<RosterResponse>> getRoster(String rosterId,Integer matchDay, ServerWebExchange exchange) {
        return rosterService.getRoster(matchDay, rosterId).map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<Player>> addOrUpdatePlayer(String rosterId, Mono<Player> player, ServerWebExchange exchange) {
        return player.flatMap(p -> rosterService.addOrUpdatePlayer(p, rosterId))
                .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<Void>> deletePlayer(String playerId, String rosterId, ServerWebExchange exchange) {
        return rosterService.deletePlayer(playerId, rosterId)
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }
}