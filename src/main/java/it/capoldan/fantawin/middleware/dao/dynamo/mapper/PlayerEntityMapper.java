package it.capoldan.fantawin.middleware.dao.dynamo.mapper;

import it.capoldan.fantawin.dto.PlayerDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.PlayerEntity;
import org.springframework.stereotype.Component;

@Component
public class PlayerEntityMapper {

    private PlayerEntityMapper() {}

    public static PlayerDto toDto(PlayerEntity entity) {
        if (entity == null) return null;
        return PlayerDto.builder()
                .playerId(entity.getPlayerId())
                .name(entity.getName())
                .realTeam(entity.getRealTeam())
                .role(entity.getRole())
                .active(entity.isActive())
                .version(entity.getVersion())
                .build();
    }

    public static PlayerEntity toEntity(PlayerDto dto) {
        if (dto == null) return null;
        return PlayerEntity.builder()
                .playerId(dto.getPlayerId())
                .name(dto.getName())
                .realTeam(dto.getRealTeam())
                .role(dto.getRole())
                .active(dto.isActive())
                .version(dto.getVersion())
                .build();
    }
}