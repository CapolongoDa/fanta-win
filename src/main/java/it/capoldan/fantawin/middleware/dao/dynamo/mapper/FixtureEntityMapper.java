package it.capoldan.fantawin.middleware.dao.dynamo.mapper;

import it.capoldan.fantawin.dto.FixtureDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.FixtureEntity;
import org.springframework.stereotype.Component;

@Component
public class FixtureEntityMapper {

    private FixtureEntityMapper() {}

    public static FixtureDto toDto(FixtureEntity entity) {
        if (entity == null) return null;
        return FixtureDto.builder()
                .matchDay(entity.getMatchday())
                .realTeam(entity.getRealTeam())
                .opponentTeam(entity.getOpponentTeam())
                .home(entity.isHome())
                .matchDifficulty(entity.getMatchDifficulty())
                .europeanCupBefore(entity.isEuropeanCupBefore())
                .europeanCupAfter(entity.isEuropeanCupAfter())
                .midweekRoundBefore(entity.isMidweekRoundBefore())
                .midweekRoundAfter(entity.isMidweekRoundAfter())
                .build();
    }

    public static FixtureEntity toEntity(FixtureDto dto) {
        if (dto == null) return null;
        return FixtureEntity.builder()
                .matchday(dto.getMatchDay())
                .realTeam(dto.getRealTeam())
                .opponentTeam(dto.getOpponentTeam())
                .home(dto.isHome())
                .matchDifficulty(dto.getMatchDifficulty())
                .europeanCupBefore(dto.isEuropeanCupBefore())
                .europeanCupAfter(dto.isEuropeanCupAfter())
                .midweekRoundBefore(dto.isMidweekRoundBefore())
                .midweekRoundAfter(dto.isMidweekRoundAfter())
                .build();
    }
}