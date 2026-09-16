package it.capoldan.fantawin.mapper;

import it.capoldan.fantawin.dto.AvailabilityStatus;
import it.capoldan.fantawin.dto.FixtureDto;
import it.capoldan.fantawin.dto.PlayerDto;
import it.capoldan.fantawin.dto.PlayerMatchStatDto;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.Player;

import java.util.List;

/**
 * Combina i dati "sparsi" sulle varie tabelle Dynamo (registry, disponibilita',
 * calendario, storico voti) nel DTO Player esposto dall'API pubblica.
 *
 * NB: Player.StatusEnum non ha ancora un valore per BALLOTTAGGIO (vedi commento
 * su mapAvailabilityStatus) - da allineare con lo YAML se si vuole preservare
 * l'informazione.
 */
public class RosterAggregationMapper {

    private RosterAggregationMapper() {}

    public static Player toApiPlayer(PlayerDto player,
                                     AvailabilityStatus availabilityStatus,
                                     Double startingProbability,
                                     FixtureDto nextFixture,
                                     List<PlayerMatchStatDto> recentStats) {

        return Player.builder()
                .id(player.getPlayerId())
                .name(player.getName())
                .realTeam(player.getRealTeam())
                .position(mapPosition(player.getRole()))
                .status(mapAvailabilityStatus(availabilityStatus))
                .nextOpponent(nextFixture != null ? nextFixture.getOpponentTeam() : null)
                .startingProbability(startingProbability != null ? startingProbability.floatValue() : null)
                .recentScores(mapRecentScores(recentStats))
                .build();
    }

    private static Player.PositionEnum mapPosition(it.capoldan.fantawin.dto.Role role) {
        if (role == null) return null;
        // i nomi coincidono 1:1 (POR, DIF, CEN, ATT) tra Role interno e PositionEnum generato
        return Player.PositionEnum.valueOf(role.name());
    }

    private static Player.StatusEnum mapAvailabilityStatus(AvailabilityStatus status) {
        if (status == null) return Player.StatusEnum.DISPONIBILE;
        return switch (status) {
            case OK -> Player.StatusEnum.DISPONIBILE;
            case INFORTUNATO -> Player.StatusEnum.INFORTUNATO;
            case SQUALIFICATO -> Player.StatusEnum.SQUALIFICATO;
            // ASSUNZIONE: BALLOTTAGGIO non esiste nello YAML, viene degradato a IN_DUBBIO.
            // Valutare di aggiungere BALLOTTAGGIO allo schema per non perdere il dato.
            case BALLOTTAGGIO, IN_DUBBIO -> Player.StatusEnum.IN_DUBBIO;
        };
    }

    private static List<Float> mapRecentScores(List<PlayerMatchStatDto> recentStats) {
        if (recentStats == null) return List.of();
        return recentStats.stream()
                .sorted((a, b) -> Integer.compare(b.getMatchDay(), a.getMatchDay())) // piu' recenti prima
                .limit(5)
                .map(PlayerMatchStatDto::getFantavoto) // ASSUNZIONE: si usa il fantavoto, non il voto puro
                .filter(java.util.Objects::nonNull)
                .map(Double::floatValue)
                .toList();
    }
}