package it.capoldan.fantawin.service;

import it.capoldan.fantawin.dto.*;
import it.capoldan.fantawin.exception.ExceptionsCodes;
import it.capoldan.fantawin.exception.IdConflictException;
import it.capoldan.fantawin.exception.InternalException;
import it.capoldan.fantawin.exception.NotFoundException;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.Player;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.RosterResponse;
import it.capoldan.fantawin.mapper.RosterAggregationMapper;
import it.capoldan.fantawin.mapper.RosterRequestMapper;
import it.capoldan.fantawin.middleware.dao.dynamo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class RosterService {

    /** Composizione fissa della rosa fantacalcistica: 3 POR + 8 DIF + 8 CEN + 6 ATT = 25.
     * Se la lega cambia regole, va reso configurabile (es. via FantaWinConfigs) invece
     * che hardcoded qui - per ora è un vincolo fisso della singola lega dell'utente. */
    private static final Map<Role, Integer> ROSTER_SLOTS = Map.of(
            Role.POR, 3,
            Role.DIF, 8,
            Role.CEN, 8,
            Role.ATT, 6
    );

    private final RosterDao rosterDao;
    private final PlayerDao playerDao;
    private final AvailabilityReportDao availabilityReportDao;
    private final FixtureDao fixtureDao;
    private final PlayerMatchStatDao playerMatchStatDao;

    public RosterService(RosterDao rosterDao,
                         PlayerDao playerDao,
                         AvailabilityReportDao availabilityReportDao,
                         FixtureDao fixtureDao,
                         PlayerMatchStatDao playerMatchStatDao) {
        this.rosterDao = rosterDao;
        this.playerDao = playerDao;
        this.availabilityReportDao = availabilityReportDao;
        this.fixtureDao = fixtureDao;
        this.playerMatchStatDao = playerMatchStatDao;
    }

    public Mono<RosterResponse> getRoster(Integer matchday, String rosterId) {
        return rosterDao.getById(rosterId)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Rosa " + rosterId + " non trovata", ExceptionsCodes.ERROR_CODE_NOT_FOUND)))
                .flatMap(roster -> Flux.fromIterable(safePlayers(roster))
                        .flatMap(entry -> buildApiPlayer(entry.getPlayerId(), matchday))
                        .collectList()
                        .map(players -> groupByPosition(roster.getTeamName(), players)));
    }

    public Mono<Player> addOrUpdatePlayer(Player request, String rosterId) {
        Role role = RosterRequestMapper.toRole(request);

        return rosterDao.getById(rosterId)
                .defaultIfEmpty(RosterDto.builder().rosterId(rosterId).players(new ArrayList<>()).build())
                .flatMap(roster -> {
                    List<RosterPlayerDto> players = new ArrayList<>(safePlayers(roster));

                    // Esclude il giocatore stesso: un update non deve essere bloccato dal proprio slot.
                    int cap = ROSTER_SLOTS.getOrDefault(role, 0);
                    long countOthers = players.stream()
                            .filter(p -> p.getFantasyRole() == role)
                            .filter(p -> !p.getPlayerId().equals(request.getId()))
                            .count();
                    if (countOthers >= cap) {
                        return Mono.error(new IdConflictException(
                                ExceptionsCodes.ERROR_CODE_GENERIC_INVALIDPARAMETER_DUPLICATED,
                                Map.of("fantasyRole", role.name() + " già al completo (" + cap + "/" + cap + ")")));
                    }

                    players.removeIf(p -> p.getPlayerId().equals(request.getId()));
                    players.add(RosterRequestMapper.toRosterPlayerDto(request));
                    roster.setPlayers(players);

                    return playerDao.getById(request.getId())
                            .map(PlayerDto::getVersion)
                            .map(Optional::of)
                            .defaultIfEmpty(Optional.<Long>empty())
                            .flatMap(existingVersion -> {
                                PlayerDto playerDto =
                                        RosterRequestMapper.toPlayerDto(request, existingVersion.orElse(null));

                                return playerDao.save(playerDto)
                                        .then(rosterDao.save(roster))
                                        .then(buildApiPlayer(request.getId(), null));
                            });
                })
                .onErrorMap(ex -> !(ex instanceof it.capoldan.fantawin.exception.RuntimeException),
                        ex -> new InternalException("Errore nel salvataggio del giocatore", ex));
    }

    public Mono<Void> deletePlayer(String playerId, String rosterId) {
        // Rimuove solo l'associazione alla rosa, non l'anagrafica del giocatore.
        return rosterDao.getById(rosterId)
                .flatMap(roster -> {
                    List<RosterPlayerDto> players = new ArrayList<>(safePlayers(roster));
                    if (!players.removeIf(p -> p.getPlayerId().equals(playerId))) {
                        return Mono.empty();
                    }
                    roster.setPlayers(players);
                    return rosterDao.save(roster);
                })
                .then();
    }

    private static List<RosterPlayerDto> safePlayers(RosterDto roster) {
        return roster.getPlayers() == null ? List.of() : roster.getPlayers();
    }

    private Mono<Player> buildApiPlayer(String playerId, Integer matchday) {
        Mono<PlayerDto> playerMono = playerDao.getById(playerId)
                .switchIfEmpty(Mono.error(new InternalException(
                        "Player " + playerId + " presente in rosa ma non trovato nel registry",
                        ExceptionsCodes.ERROR_CODE_GENERIC_ERROR)));

        Mono<AvailabilityReportDto> availabilityMono = availabilityReportDao.getById(playerId)
                .defaultIfEmpty(AvailabilityReportDto.builder()
                        .playerId(playerId)
                        .status(AvailabilityStatus.OK)
                        .startingProbability(100.0)
                        .build());

        Mono<List<PlayerMatchStatDto>> recentStatsMono = playerMatchStatDao.findByPlayer(playerId).collectList();

        return Mono.zip(playerMono, availabilityMono, recentStatsMono)
                .flatMap(tuple -> {
                    PlayerDto player = tuple.getT1();
                    AvailabilityReportDto availability = tuple.getT2();
                    List<PlayerMatchStatDto> recentStats = tuple.getT3();

                    Mono<FixtureDto> nextFixtureMono = (matchday == null)
                            ? Mono.empty()
                            : fixtureDao.findByMatchday(matchday)
                            .filter(f -> f.getRealTeam().equals(player.getRealTeam()))
                            .next();

                    return nextFixtureMono
                            .map(Optional::of)
                            .defaultIfEmpty(Optional.empty())
                            .map(fixtureOpt -> RosterAggregationMapper.toApiPlayer(
                                    player,
                                    availability.getStatus(),
                                    availability.getStartingProbability(),
                                    fixtureOpt.orElse(null),
                                    recentStats));
                });
    }

    private RosterResponse groupByPosition(String teamName, List<Player> players) {
        RosterResponse response = new RosterResponse();
        response.setTeamName(teamName);
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