package it.capoldan.fantawin.middleware.dao.dynamo.mapper;

import it.capoldan.fantawin.dto.PlayerMatchStatDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.PlayerMatchStatEntity;
import org.springframework.stereotype.Component;

@Component
public class PlayerMatchStatEntityMapper {

    private PlayerMatchStatEntityMapper() {}

    public static PlayerMatchStatDto toDto(PlayerMatchStatEntity entity) {
        if (entity == null) return null;
        return PlayerMatchStatDto.builder()
                .playerId(entity.getPlayerId())
                .matchDay(entity.getMatchday())
                .season(entity.getSeason())
                .opponentTeam(entity.getOpponentTeam())
                .home(entity.isHome())
                .voto(entity.getVoto())
                .fantavoto(entity.getFantavoto())
                .gol(entity.getGol())
                .assist(entity.getAssist())
                .ammonizioni(entity.getAmmonizioni())
                .espulsioni(entity.getEspulsioni())
                .xg(entity.getXg())
                .xa(entity.getXa())
                .build();
    }

    public static PlayerMatchStatEntity toEntity(PlayerMatchStatDto dto) {
        if (dto == null) return null;
        return PlayerMatchStatEntity.builder()
                .playerId(dto.getPlayerId())
                .matchday(dto.getMatchDay())
                .season(dto.getSeason())
                .opponentTeam(dto.getOpponentTeam())
                .home(dto.isHome())
                .voto(dto.getVoto())
                .fantavoto(dto.getFantavoto())
                .gol(dto.getGol())
                .assist(dto.getAssist())
                .ammonizioni(dto.getAmmonizioni())
                .espulsioni(dto.getEspulsioni())
                .xg(dto.getXg())
                .xa(dto.getXa())
                .build();
    }
}