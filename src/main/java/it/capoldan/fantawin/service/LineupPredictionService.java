package it.capoldan.fantawin.service;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.dto.*;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.*;
import it.capoldan.fantawin.middleware.dao.dynamo.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class LineupPredictionService {
    // modulo -> [difensori, centrocampisti, attaccanti] (portiere sempre 1, implicito)
    private static final Map<String, int[]> FORMATIONS = Map.of(
            "3-4-3", new int[]{3, 4, 3},
            "4-3-3", new int[]{4, 3, 3},
            "3-5-2", new int[]{3, 5, 2},
            "4-4-2", new int[]{4, 4, 2}
    );

    private final RosterEntryDao rosterEntryDao;
    private final PlayerDao playerDao;
    private final AvailabilityReportDao availabilityReportDao;
    private final FixtureDao fixtureDao;
    private final PlayerMatchStatDao playerMatchStatDao;
    private final PlayerFormAggregationService formAggregationService;
    private final FantaWinConfigs fantaWinConfigs;

    public Mono<LineupResponse> calculateOptimalLineup(LineupRequest request,String rosterId) {
        return rosterEntryDao.findByRoster(rosterId)
                .flatMap(entry -> buildRatingDetails(entry.getPlayerId(), request))
                .collectList()
                .map(allPlayers -> selectBestFormation(allPlayers, request));
    }

    private Mono<PlayerRatingDetails> buildRatingDetails(String playerId, LineupRequest request) {
        Mono<PlayerDto> playerMono = playerDao.getById(playerId);
        Mono<AvailabilityReportDto> availabilityMono = availabilityReportDao.getById(playerId)
                .defaultIfEmpty(AvailabilityReportDto.builder()
                        .playerId(playerId)
                        .status(AvailabilityStatus.OK)
                        .startingProbability(100.0)
                        .build());

        return playerMono.flatMap(player -> {
            Mono<FixtureDto> fixtureMono = fixtureDao.getByMatchdayAndTeam(request.getMatchday(), player.getRealTeam());

            return Mono.zip(availabilityMono, fixtureMono.defaultIfEmpty(FixtureDto.builder().build()))
                    .flatMap(tuple -> {
                        AvailabilityReportDto availability = tuple.getT1();
                        FixtureDto fixture = tuple.getT2();

                        return formAggregationService.computeFormComponents(playerId, fixture.getOpponentTeam())
                                .map(form -> computeRating(player, availability, fixture, form, request));
                    });
        });
    }

    private PlayerRatingDetails computeRating(PlayerDto player, AvailabilityReportDto availability,
                                              FixtureDto fixture, PlayerFormAggregationService.FormComponents form,
                                              LineupRequest request) {

        // --- fix #6: esclusione tassativa, priorità massima su qualsiasi altro calcolo ---
        if (availability.getStatus() == AvailabilityStatus.INFORTUNATO
                || availability.getStatus() == AvailabilityStatus.SQUALIFICATO) {
            return buildDetails(player, fixture, 0.0, FantaRatingBreakdown.builder()
                    .baseFormScore(0.0f).historicalScore(0.0f).matchDifficultyXgScore(0.0f)
                    .midweekPenalty(0.0f).availabilityPenalty(0.0f).build());
        }

        double matchDifficultyXg = matchDifficultyComponent(fixture.getMatchDifficulty()) * 0.5 + form.xgXaScore() * 0.5;
        double baseRating = form.recentFormScore() * 0.4
                + form.historicalVsOpponentScore() * 0.3
                + matchDifficultyXg * 0.3;

        // --- fix #7: malus turnover differenziato Big vs Riserve ---
        double midweekPenalty = 0.0;
        boolean hasMidweekOrEuropean = fixture.isEuropeanCupBefore() || fixture.isEuropeanCupAfter()
                || fixture.isMidweekRoundBefore() || fixture.isMidweekRoundAfter()
                || (request.getTeamsWithMidweekMatches() != null
                && request.getTeamsWithMidweekMatches().contains(player.getRealTeam())); // override manuale
        if (hasMidweekOrEuropean) {
            boolean isBig = form.recentFormScore() >= fantaWinConfigs.getBigPlayerThreshold();
            midweekPenalty = isBig ? -1.5 : -2.5;
        }

        // --- fix #8: "in dubbio" (stato) e "ballottaggio %" (probabilità) sono regole distinte ---
        double availabilityPenalty = 0.0;
        Double startingProb = availability.getStartingProbability();
        if (startingProb != null && startingProb < 50.0) {
            availabilityPenalty = -2.0;
        } else if (startingProb != null && startingProb <= 75.0 && startingProb >= 55.0) {
            availabilityPenalty = -0.8;
        } else if (availability.getStatus() == AvailabilityStatus.IN_DUBBIO) {
            availabilityPenalty = -1.0;
        }

        double finalRating = Math.max(0.0, Math.min(10.0, baseRating + midweekPenalty + availabilityPenalty));

        FantaRatingBreakdown breakdown = FantaRatingBreakdown.builder()
                .baseFormScore((float) (form.recentFormScore() * 0.4))
                .historicalScore((float) (form.historicalVsOpponentScore() * 0.3))
                .matchDifficultyXgScore((float) (matchDifficultyXg * 0.3))
                .midweekPenalty((float) midweekPenalty)
                .availabilityPenalty((float) availabilityPenalty)
                .build();

        return buildDetails(player, fixture, finalRating, breakdown);
    }

    private double matchDifficultyComponent(Double matchDifficulty) {
        if (matchDifficulty == null) return 5.0; // neutro se dato mancante
        return Math.max(0.0, 10.0 - matchDifficulty); // avversario piu' difficile = componente piu' bassa
    }

    private PlayerRatingDetails buildDetails(PlayerDto player, FixtureDto fixture, double rating, FantaRatingBreakdown breakdown) {
        Player apiPlayer = Player.builder()
                .id(player.getPlayerId())
                .name(player.getName())
                .realTeam(player.getRealTeam())
                .position(Player.PositionEnum.valueOf(player.getRole().name()))
                .nextOpponent(fixture.getOpponentTeam())
                .build();

        return PlayerRatingDetails.builder()
                .player(apiPlayer)
                .fantaRating((float) rating)
                .fantaRatingBreakdown(breakdown)
                .build();
    }

    // --- fix #9: simulazione reale dei moduli + modificatore di difesa + panchina ---

    private LineupResponse selectBestFormation(List<PlayerRatingDetails> allPlayers, LineupRequest request) {
        List<PlayerRatingDetails> eligible = allPlayers.stream()
                .filter(p -> p.getFantaRating() > 0.0) // esclude infortunati/squalificati anche dalla panchina
                .toList();

        Map<String, List<PlayerRatingDetails>> byRole = eligible.stream()
                .collect(Collectors.groupingBy(p -> p.getPlayer().getPosition().name()));

        byRole.values().forEach(list -> list.sort(Comparator.comparing(PlayerRatingDetails::getFantaRating).reversed()));

        List<PlayerRatingDetails> goalkeepers = byRole.getOrDefault("POR", List.of());
        if (goalkeepers.isEmpty()) {
            throw new it.capoldan.fantawin.exception.InternalException(
                    "Nessun portiere disponibile per il calcolo",
                    it.capoldan.fantawin.exception.ExceptionsCodes.ERROR_CODE_GENERIC_ERROR);
        }

        String bestFormation = null;
        List<PlayerRatingDetails> bestEleven = null;
        double bestScore = -1.0;
        int bestDefenseBonus = 0;

        for (Map.Entry<String, int[]> formationEntry : FORMATIONS.entrySet()) {
            int[] shape = formationEntry.getValue();
            int defCount = shape[0], midCount = shape[1], fwdCount = shape[2];

            List<PlayerRatingDetails> defenders = topN(byRole.get("DIF"), defCount);
            List<PlayerRatingDetails> midfielders = topN(byRole.get("CEN"), midCount);
            List<PlayerRatingDetails> forwards = topN(byRole.get("ATT"), fwdCount);

            if (defenders == null || midfielders == null || forwards == null) {
                continue; // rosa insufficiente per questo modulo, si scarta
            }

            List<PlayerRatingDetails> eleven = new ArrayList<>();
            eleven.add(goalkeepers.get(0));
            eleven.addAll(defenders);
            eleven.addAll(midfielders);
            eleven.addAll(forwards);

            double baseTotal = eleven.stream().mapToDouble(PlayerRatingDetails::getFantaRating).sum();

            int defenseBonus = 0;
            if (Boolean.TRUE.equals(request.getModifierActive()) && defCount == 4) {
                defenseBonus = computeDefenseModifierBonus(goalkeepers.get(0).getPlayer().getId(), defenders);
            }

            double totalScore = baseTotal + defenseBonus;

            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestFormation = formationEntry.getKey();
                bestEleven = eleven;
                bestDefenseBonus = defenseBonus;
            }
        }

        if (bestFormation == null) {
            throw new it.capoldan.fantawin.exception.InternalException(
                    "Rosa insufficiente per comporre uno qualsiasi dei moduli previsti",
                    it.capoldan.fantawin.exception.ExceptionsCodes.ERROR_CODE_GENERIC_ERROR);
        }

        List<PlayerRatingDetails> bench = buildBench(eligible, bestEleven, goalkeepers.get(0));

        return LineupResponse.builder()
                .winningFormation(bestFormation)
                .totalExpectedScore((float) bestScore)
                .defenseModifierBonus(bestDefenseBonus)
                .startingEleven(bestEleven)
                .bench(bench)
                .build();
    }

    private List<PlayerRatingDetails> topN(List<PlayerRatingDetails> candidates, int n) {
        if (candidates == null || candidates.size() < n) return null;
        return new ArrayList<>(candidates.subList(0, n));
    }

    /**
     * "Voto puro atteso" != FantaRating (che include bonus/malus pesati). Uso la media
     * dei recenti "voto" reali (non fantavoto) come proxy, coerente col significato
     * della regola 4 del README.
     */
    private int computeDefenseModifierBonus(String goalkeeperId, List<PlayerRatingDetails> defenders) {
        List<String> ids = new ArrayList<>();
        ids.add(goalkeeperId);
        defenders.forEach(d -> ids.add(d.getPlayer().getId()));

        double avgPureVote = ids.stream()
                .mapToDouble(this::averageRecentPureVote)
                .average()
                .orElse(0.0);

        if (avgPureVote >= 7.0) return 6;
        if (avgPureVote >= 6.5) return 3;
        if (avgPureVote >= 6.0) return 1;
        return 0;
    }

    private double averageRecentPureVote(String playerId) {
        return playerMatchStatDao.findByPlayer(playerId)
                .collectList()
                .map(history -> history.stream()
                        .sorted(Comparator.comparing(PlayerMatchStatDto::getMatchDay).reversed())
                        .limit(5)
                        .filter(m -> m.getVoto() != null)
                        .mapToDouble(PlayerMatchStatDto::getVoto)
                        .average()
                        .orElse(0.0))
                .block(); // uso puntuale e limitato (max 4 giocatori), accettabile qui
    }

    /** fix #9 - Blindatura panchina anti-voto-zero */
    private List<PlayerRatingDetails> buildBench(List<PlayerRatingDetails> eligible,
                                                 List<PlayerRatingDetails> starters,
                                                 PlayerRatingDetails startingGk) {
        Set<String> starterIds = starters.stream().map(p -> p.getPlayer().getId()).collect(Collectors.toSet());
        List<PlayerRatingDetails> remaining = eligible.stream()
                .filter(p -> !starterIds.contains(p.getPlayer().getId()))
                .toList();

        List<PlayerRatingDetails> backupGks = remaining.stream()
                .filter(p -> p.getPlayer().getPosition() == Player.PositionEnum.POR)
                .sorted(Comparator
                        // priorita' al secondo portiere della stessa squadra reale del titolare
                        .comparing((PlayerRatingDetails p) -> !p.getPlayer().getRealTeam().equals(startingGk.getPlayer().getRealTeam()))
                        .thenComparing(Comparator.comparing(PlayerRatingDetails::getFantaRating).reversed()))
                .toList();

        List<PlayerRatingDetails> outfieldBench = remaining.stream()
                .filter(p -> p.getPlayer().getPosition() != Player.PositionEnum.POR)
                .sorted(Comparator
                        // "certezza del voto" = probabilita' di titolarita' piu' alta prima
                        .comparing((PlayerRatingDetails p) -> -safeStartingProbability(p.getPlayer()))
                        .thenComparing(Comparator.comparing(PlayerRatingDetails::getFantaRating).reversed()))
                .toList();

        List<PlayerRatingDetails> bench = new ArrayList<>();
        bench.addAll(backupGks);
        bench.addAll(outfieldBench);
        return bench;
    }

    private double safeStartingProbability(Player player) {
        return player.getStartingProbability() != null ? player.getStartingProbability() : 0.0;
    }
}