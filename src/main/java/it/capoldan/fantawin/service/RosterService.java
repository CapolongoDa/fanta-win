package it.capoldan.fantawin.service;

import it.capoldan.fantawin.dto.*;
import it.capoldan.fantawin.exception.InternalException;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.Player;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.RosterResponse;
import it.capoldan.fantawin.mapper.RosterAggregationMapper;
import it.capoldan.fantawin.middleware.dao.dynamo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
public class RosterService {

    /** App single-user: un'unica rosa. Da parametrizzare se in futuro serve multi-lega. */
    private static final String DEFAULT_ROSTER_ID = "DEFAULT";

    private final RosterEntryDao rosterEntryDao;
    private final PlayerDao playerDao;
    private final AvailabilityReportDao availabilityReportDao;
    private final FixtureDao fixtureDao;
    private final PlayerMatchStatDao playerMatchStatDao;

    public RosterService(RosterEntryDao rosterEntryDao,
                         PlayerDao playerDao,
                         AvailabilityReportDao availabilityReportDao,
                         FixtureDao fixtureDao,
                         PlayerMatchStatDao playerMatchStatDao) {
        this.rosterEntryDao = rosterEntryDao;
        this.playerDao = playerDao;
        this.availabilityReportDao = availabilityReportDao;
        this.fixtureDao = fixtureDao;
        this.playerMatchStatDao = playerMatchStatDao;
    }

    /**
     * @param matchday giornata corrente, usata per calcolare nextOpponent. Vedi nota
     *                  sull'assenza di questo parametro nell'attuale contratto GET /roster.
     */
    public Mono<RosterResponse> getRoster(Integer matchday) {
        return rosterEntryDao.findByRoster(DEFAULT_ROSTER_ID)
                .flatMap(entry -> buildApiPlayer(entry.getPlayerId(), matchday))
                .collectList()
                .map(this::groupByPosition);
    }

    public Mono<Player> addOrUpdatePlayer(Player request) {
        PlayerDto playerDto = PlayerDto.builder()
                .playerId(request.getId())
                .name(request.getName())
                .realTeam(request.getRealTeam())
                .role(it.capoldan.fantawin.dto.Role.valueOf(request.getPosition().name()))
                .active(true)
                .build();

        RosterEntryDto rosterEntryDto = RosterEntryDto.builder()
                .rosterId(DEFAULT_ROSTER_ID)
                .playerId(request.getId())
                .fantasyRole(playerDto.getRole())
                .build();

        return playerDao.save(playerDto)
                .then(rosterEntryDao.save(rosterEntryDto))
                .then(buildApiPlayer(request.getId(), null))
                .onErrorMap(ex -> !(ex instanceof it.capoldan.fantawin.exception.RuntimeException),
                        ex -> new InternalException("Errore nel salvataggio del giocatore", ex));
    }

    public Mono<Void> deletePlayer(String playerId) {
        // Rimuove solo l'associazione alla rosa, non l'anagrafica del giocatore
        // (che resta utile per storico/statistiche condivise tra leghe future).
        return rosterEntryDao.delete(DEFAULT_ROSTER_ID, playerId).then();
    }

    private Mono<Player> buildApiPlayer(String playerId, Integer matchday) {
    Mono<PlayerDto> playerMono = playerDao.getById(playerId)
            .switchIfEmpty(Mono.error(new InternalException(
                    "Player " + playerId + " presente in rosa ma non trovato nel registry",
                    it.capoldan.fantawin.exception.ExceptionsCodes.ERROR_CODE_GENERIC_ERROR))); // Todo: far salire 404

    Mono<AvailabilityReportDto> availabilityMono = availabilityReportDao.getById(playerId)
            .defaultIfEmpty(AvailabilityReportDto.builder()
                    .playerId(playerId)
                    .status(it.capoldan.fantawin.dto.AvailabilityStatus.OK)
                    .startingProbability(100.0)
                    .build());

    Mono<List<PlayerMatchStatDto>> recentStatsMono = playerMatchStatDao.findByPlayer(playerId).collectList();

    Mono<List<FixtureDto>> matchdayFixturesMono = (matchday == null)
            ? Mono.just(List.of())
            : fixtureDao.findByMatchday(matchday).collectList();

    return Mono.zip(playerMono, availabilityMono, recentStatsMono, matchdayFixturesMono)
            .map(tuple -> {
                PlayerDto player = tuple.getT1();
                AvailabilityReportDto availability = tuple.getT2();
                List<PlayerMatchStatDto> recentStats = tuple.getT3();

                FixtureDto nextFixture = tuple.getT4().stream()
                        .filter(f -> f.getRealTeam().equals(player.getRealTeam()))
                        .findFirst()
                        .orElse(null);

                return RosterAggregationMapper.toApiPlayer(
                        player,
                        availability.getStatus(),
                        availability.getStartingProbability(),
                        nextFixture,
                        recentStats);
            });
}

    private RosterResponse groupByPosition(List<Player> players) {
        RosterResponse response = new RosterResponse();
        response.setGoalkeepers(filterByPosition(players, Player.PositionEnum.POR));
        response.setDefenders(filterByPosition(players, Player.PositionEnum.DIF));
        response.setMidfielders(filterByPosition(players, Player.PositionEnum.CEN));
        response.setForwards(filterByPosition(players, Player.PositionEnum.ATT));
        return response;
    }

    private List<Player> filterByPosition(List<Player> players, Player.PositionEnum position) {
        return players.stream().filter(p -> p.getPosition() == position).toList();
    }
}