package it.capoldan.fantawin.service;

import it.capoldan.fantawin.dto.*;
import it.capoldan.fantawin.exception.ExceptionsCodes;
import it.capoldan.fantawin.exception.NotFoundException;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.LineupRequest;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.LineupResponse;
import it.capoldan.fantawin.middleware.dao.dynamo.AvailabilityReportDao;
import it.capoldan.fantawin.middleware.dao.dynamo.FixtureDao;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerDao;
import it.capoldan.fantawin.middleware.dao.dynamo.RosterDao;
import it.capoldan.fantawin.utils.FormationSelector;
import it.capoldan.fantawin.utils.PlayerRatingCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LineupPredictionService {

    private final RosterDao rosterDao;
    private final PlayerDao playerDao;
    private final AvailabilityReportDao availabilityReportDao;
    private final FixtureDao fixtureDao;
    private final PlayerFormAggregationService formAggregationService;
    private final PlayerRatingCalculator ratingCalculator;
    private final FormationSelector formationSelector;

    public LineupPredictionService(RosterDao rosterDao,
                                   PlayerDao playerDao,
                                   AvailabilityReportDao availabilityReportDao,
                                   FixtureDao fixtureDao,
                                   PlayerFormAggregationService formAggregationService,
                                   PlayerRatingCalculator ratingCalculator,
                                   FormationSelector formationSelector) {
        this.rosterDao = rosterDao;
        this.playerDao = playerDao;
        this.availabilityReportDao = availabilityReportDao;
        this.fixtureDao = fixtureDao;
        this.formAggregationService = formAggregationService;
        this.ratingCalculator = ratingCalculator;
        this.formationSelector = formationSelector;
    }

    public Mono<LineupResponse> calculateOptimalLineup(LineupRequest request, String rosterId) {
        return rosterDao.getById(rosterId)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Rosa " + rosterId + " non trovata", ExceptionsCodes.ERROR_CODE_NOT_FOUND)))
                .flatMap(roster -> buildCalculations(roster.getPlayers(), request))
                .map(allCalculations -> formationSelector.select(allCalculations, request));
    }

    private Mono<List<PlayerCalculation>> buildCalculations(List<RosterPlayerDto> rosterPlayers, LineupRequest request) {
        if (rosterPlayers == null || rosterPlayers.isEmpty()) {
            return Mono.just(List.of());
        }

        return Flux.fromIterable(rosterPlayers)
                .flatMap(rosterPlayer -> playerDao.getById(rosterPlayer.getPlayerId()))
                .collectList()
                .flatMap(players -> fixturesByRealTeam(players, request.getMatchday())
                        .flatMap(fixturesByTeam -> Flux.fromIterable(players)
                                .flatMap(player -> buildCalculation(player, fixturesByTeam.get(player.getRealTeam()), request))
                                .collectList()));
    }

    /** Recupera la fixture della giornata una sola volta per ciascuna squadra reale distinta
     * presente in rosa, invece che una volta per ogni giocatore: in una rosa di 25 e' comune
     * avere piu' giocatori della stessa squadra reale, che condividono la stessa fixture. */
    private Mono<Map<String, FixtureDto>> fixturesByRealTeam(List<PlayerDto> players, Integer matchday) {
        Set<String> realTeams = players.stream()
                .map(PlayerDto::getRealTeam)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return Flux.fromIterable(realTeams)
                .flatMap(team -> fixtureDao.getByMatchdayAndTeam(matchday, team)
                        .map(fixture -> Map.entry(team, fixture)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private Mono<PlayerCalculation> buildCalculation(PlayerDto player, FixtureDto fixture, LineupRequest request) {
        FixtureDto safeFixture = fixture != null ? fixture : FixtureDto.builder().build();
        String playerId = player.getPlayerId();

        Mono<AvailabilityReportDto> availabilityMono = availabilityReportDao.getById(playerId)
                .defaultIfEmpty(AvailabilityReportDto.builder()
                        .playerId(playerId)
                        .status(AvailabilityStatus.OK)
                        .startingProbability(100.0)
                        .build());

        return availabilityMono.flatMap(availability ->
                formAggregationService.computeFormComponents(playerId, safeFixture.getOpponentTeam())
                        .map(form -> ratingCalculator.compute(player, availability, safeFixture, form, request)));
    }
}