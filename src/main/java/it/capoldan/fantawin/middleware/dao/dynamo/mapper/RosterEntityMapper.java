package it.capoldan.fantawin.middleware.dao.dynamo.mapper;

import it.capoldan.fantawin.dto.RosterDto;
import it.capoldan.fantawin.dto.RosterPlayerDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.RosterEntity;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.RosterPlayerEntity;

import java.util.List;

public class RosterEntityMapper {

    private RosterEntityMapper() {}

    public static RosterDto toDto(RosterEntity entity) {
        if (entity == null) return null;
        return RosterDto.builder()
                .rosterId(entity.getRosterId())
                .teamName(entity.getTeamName())
                .players(entity.getPlayers() == null ? List.of()
                        : entity.getPlayers().stream()
                        .map(RosterEntityMapper::toPlayerDto)
                        .toList())
                .ownerId(entity.getOwnerId())
                .build();
    }

    public static RosterEntity toEntity(RosterDto dto) {
        if (dto == null) return null;
        return RosterEntity.builder()
                .rosterId(dto.getRosterId())
                .teamName(dto.getTeamName())
                .players(dto.getPlayers() == null ? List.of()
                        : dto.getPlayers().stream()
                        .map(RosterEntityMapper::toPlayerEntity)
                        .toList())
                .ownerId(dto.getOwnerId())
                .build();
    }

    private static RosterPlayerDto toPlayerDto(RosterPlayerEntity entity) {
        return RosterPlayerDto.builder()
                .playerId(entity.getPlayerId())
                .fantasyRole(entity.getFantasyRole())
                .build();
    }

    private static RosterPlayerEntity toPlayerEntity(RosterPlayerDto dto) {
        return RosterPlayerEntity.builder()
                .playerId(dto.getPlayerId())
                .fantasyRole(dto.getFantasyRole())
                .build();
    }
}