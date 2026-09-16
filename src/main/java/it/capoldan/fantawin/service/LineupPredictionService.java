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
        log.info("Calcolo formazione ottimale per rosterId={} matchday={} modifierActive={}",
                rosterId, request.getMatchday(), request.getModifierActive());
        return rosterDao.getById(rosterId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Rosa {} non trovata: impossibile calcolare la formazione", rosterId);
                    return Mono.error(new NotFoundException(
                            "Rosa " + rosterId + " non trovata", ExceptionsCodes.ERROR_CODE_NOT_FOUND));
                }))
                .flatMapMany(roster -> buildCalculations(roster.getPlayers(), request))
                .collectList()
                .map(allCalculations -> formationSelector.select(allCalculations, request))
                .doOnSuccess(response -> log.info(
                        "Formazione calcolata per rosterId={}: modulo={} punteggioTotale={} bonusDifesa={}",
                        rosterId, response.getWinningFormation(), response.getTotalExpectedScore(), response.getDefenseModifierBonus()))
                .doOnError(ex -> log.warn("Errore nel calcolo della formazione per rosterId={}", rosterId, ex));
    }

    private Flux<PlayerCalculation> buildCalculations(List<RosterPlayerDto> players, LineupRequest request) {
        if (players == null || players.isEmpty()) {
            log.warn("Rosa senza giocatori: nessun calcolo possibile per la giornata {}", request.getMatchday());
            return Flux.empty();
        }
        return Flux.fromIterable(players)
                .flatMap(rosterPlayer -> buildCalculation(rosterPlayer.getPlayerId(), request));
    }

    private Mono<PlayerCalculation> buildCalculation(String playerId, LineupRequest request) {
        Mono<AvailabilityReportDto> availabilityMono = availabilityReportDao.getById(playerId)
                .defaultIfEmpty(AvailabilityReportDto.builder()
                        .playerId(playerId)
                        .status(AvailabilityStatus.OK)
                        .startingProbability(100.0)
                        .build());

        return playerDao.getById(playerId).flatMap(player -> {
            Mono<FixtureDto> fixtureMono = fixtureDao.getByMatchdayAndTeam(request.getMatchday(), player.getRealTeam())
                    .defaultIfEmpty(FixtureDto.builder().build())
                    .doOnNext(fixture -> {
                        if (fixture.getRealTeam() == null) {
                            log.warn("Nessuna fixture trovata per realTeam={} matchday={}: FantaRating calcolato senza dati di difficolta' match",
                                    player.getRealTeam(), request.getMatchday());
                        }
                    });

            return Mono.zip(availabilityMono, fixtureMono)
                    .flatMap(tuple -> {
                        AvailabilityReportDto availability = tuple.getT1();
                        FixtureDto fixture = tuple.getT2();
                        return formAggregationService.computeFormComponents(playerId, fixture.getOpponentTeam())
                                .map(form -> ratingCalculator.compute(player, availability, fixture, form, request));
                    });
        });
    }
}
