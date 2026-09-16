package it.capoldan.fantawin.middleware.dao.dynamo.mapper;

import it.capoldan.fantawin.dto.RosterEntryDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.RosterEntryEntity;
import org.springframework.stereotype.Component;

@Component
public class RosterEntryEntityMapper {

    private RosterEntryEntityMapper() {}

    public static RosterEntryDto toDto(RosterEntryEntity entity) {
        if (entity == null) return null;
        return RosterEntryDto.builder()
                .rosterId(entity.getRosterId())
                .playerId(entity.getPlayerId())
                .fantasyRole(entity.getFantasyRole())
                .build();
    }

    public static RosterEntryEntity toEntity(RosterEntryDto dto) {
        if (dto == null) return null;
        return RosterEntryEntity.builder()
                .rosterId(dto.getRosterId())
                .playerId(dto.getPlayerId())
                .fantasyRole(dto.getFantasyRole())
                .build();
    }
}