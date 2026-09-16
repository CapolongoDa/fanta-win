package it.capoldan.fantawin.mapper;

import it.capoldan.fantawin.dto.PlayerDto;
import it.capoldan.fantawin.dto.Role;
import it.capoldan.fantawin.dto.RosterPlayerDto;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.Player;

/**
 * Converte i DTO esposti dall'API pubblica nei DTO interni di dominio.
 * Direzione opposta rispetto a RosterAggregationMapper (interno -> API).
 */
public class RosterRequestMapper {

    private RosterRequestMapper() {}

    /** POR/DIF/CEN/ATT coincidono 1:1 tra PositionEnum generato e Role interno. */
    public static Role toRole(Player apiPlayer) {
        return Role.valueOf(apiPlayer.getPosition().name());
    }

    public static PlayerDto toPlayerDto(Player apiPlayer, Long existingVersion) {
        return PlayerDto.builder()
                .playerId(apiPlayer.getId())
                .name(apiPlayer.getName())
                .realTeam(apiPlayer.getRealTeam())
                .role(toRole(apiPlayer))
                .active(true)
                .version(existingVersion)
                .build();
    }

    public static RosterPlayerDto toRosterPlayerDto(Player apiPlayer) {
        return RosterPlayerDto.builder()
                .playerId(apiPlayer.getId())
                .fantasyRole(toRole(apiPlayer))
                .build();
    }
}