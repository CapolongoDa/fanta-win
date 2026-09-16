package it.capoldan.fantawin.middleware.dao.dynamo.mapper;

import it.capoldan.fantawin.dto.AvailabilityReportDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.AvailabilityReportEntity;
import org.springframework.stereotype.Component;

@Component
public class AvailabilityReportEntityMapper {

    private AvailabilityReportEntityMapper() {}

    public static AvailabilityReportDto toDto(AvailabilityReportEntity entity) {
        if (entity == null) return null;
        return AvailabilityReportDto.builder()
                .playerId(entity.getPlayerId())
                .status(entity.getStatus())
                .startingProbability(entity.getStartingProbability())
                .note(entity.getNote())
                .lastUpdated(entity.getLastUpdated())
                .build();
    }

    public static AvailabilityReportEntity toEntity(AvailabilityReportDto dto) {
        if (dto == null) return null;
        return AvailabilityReportEntity.builder()
                .playerId(dto.getPlayerId())
                .status(dto.getStatus())
                .startingProbability(dto.getStartingProbability())
                .note(dto.getNote())
                .lastUpdated(dto.getLastUpdated())
                .build();
    }
}