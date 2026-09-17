package it.capoldan.fantawin.middleware.dao.dynamo.mapper;

import it.capoldan.fantawin.dto.PlayerCatalogDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.PlayerCatalogEntity;
import org.springframework.stereotype.Component;

@Component
public class PlayerCatalogEntityMapper {

    private PlayerCatalogEntityMapper() {
    }

    public static PlayerCatalogDto toDto(PlayerCatalogEntity entity) {
        if (entity == null) return null;
        return PlayerCatalogDto.builder()
                .catalogId(entity.getCatalogId())
                .nome(entity.getNome())
                .ruolo(entity.getRuolo())
                .squadra(entity.getSquadra())
                .build();
    }

    public static PlayerCatalogEntity toEntity(PlayerCatalogDto dto) {
        if (dto == null) return null;
        return PlayerCatalogEntity.builder()
                .catalogId(dto.getCatalogId())
                .nome(dto.getNome())
                .ruolo(dto.getRuolo())
                .squadra(dto.getSquadra())
                .build();
    }
}
