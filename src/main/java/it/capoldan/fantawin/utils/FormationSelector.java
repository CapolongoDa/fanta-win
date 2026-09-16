package it.capoldan.fantawin.utils;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.dto.PlayerCalculation;
import it.capoldan.fantawin.exception.IncompleteRosterException;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.LineupRequest;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.LineupResponse;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.Player;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.PlayerRatingDetails;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FormationSelector {

    private final FantaWinConfigs configs;

    public FormationSelector(FantaWinConfigs configs) {
        this.configs = configs;
    }

    public LineupResponse select(List<PlayerCalculation> allCalculations, LineupRequest request) {
        List<PlayerCalculation> eligible = allCalculations.stream()
                .filter(c -> c.details().getFantaRating() > 0.0)
                .toList();

        Map<String, Double> pureVoteById = allCalculations.stream()
                .collect(Collectors.toMap(c -> c.details().getPlayer().getId(), PlayerCalculation::recentPureVoteAverage));

        Map<String, List<PlayerRatingDetails>> byRole = eligible.stream()
                .map(PlayerCalculation::details)
                .collect(Collectors.groupingBy(p -> p.getPlayer().getPosition().name()));

        byRole.values().forEach(list -> list.sort(Comparator.comparing(PlayerRatingDetails::getFantaRating).reversed()));

        List<PlayerRatingDetails> goalkeepers = byRole.getOrDefault("POR", List.of());
        if (goalkeepers.isEmpty()) {
            throw new IncompleteRosterException("Nessun portiere disponibile per il calcolo: verifica stato e disponibilita' dei portieri in rosa");
        }

        String bestFormation = null;
        List<PlayerRatingDetails> bestEleven = null;
        double bestScore = -1.0;
        int bestDefenseBonus = 0;

        for (Map.Entry<String, List<Integer>> formationEntry : configs.getFormations().entrySet()) {
            List<Integer> shape = formationEntry.getValue();
            int defCount = shape.get(0),
                    midCount = shape.get(1),
                    fwdCount = shape.get(2);

            List<PlayerRatingDetails> defenders = topN(byRole.get("DIF"), defCount);
            List<PlayerRatingDetails> midfielders = topN(byRole.get("CEN"), midCount);
            List<PlayerRatingDetails> forwards = topN(byRole.get("ATT"), fwdCount);

            if (defenders == null || midfielders == null || forwards == null) {
                continue;
            }

            List<PlayerRatingDetails> eleven = new ArrayList<>();
            eleven.add(goalkeepers.get(0));
            eleven.addAll(defenders);
            eleven.addAll(midfielders);
            eleven.addAll(forwards);

            double baseTotal = eleven.stream().mapToDouble(PlayerRatingDetails::getFantaRating).sum();

            int defenseBonus = 0;
            if (Boolean.TRUE.equals(request.getModifierActive()) && defCount == 4) {
                defenseBonus = computeDefenseModifierBonus(goalkeepers.get(0), defenders, pureVoteById);
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
            throw new IncompleteRosterException("Rosa insufficiente per comporre uno qualsiasi dei moduli previsti (3-4-3, 4-3-3, 3-5-2, 4-4-2)");
        }

        List<PlayerRatingDetails> eligibleDetails = eligible.stream().map(PlayerCalculation::details).toList();
        List<PlayerRatingDetails> bench = buildBench(eligibleDetails, bestEleven, goalkeepers.get(0));

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

    private int computeDefenseModifierBonus(PlayerRatingDetails goalkeeper, List<PlayerRatingDetails> defenders,
                                            Map<String, Double> pureVoteById) {
        List<PlayerRatingDetails> group = new ArrayList<>();
        group.add(goalkeeper);
        group.addAll(defenders);

        double avgPureVote = group.stream()
                .mapToDouble(p -> pureVoteById.getOrDefault(p.getPlayer().getId(), 0.0))
                .average()
                .orElse(0.0);

        if (avgPureVote >= 7.0) return 6;
        if (avgPureVote >= 6.5) return 3;
        if (avgPureVote >= 6.0) return 1;
        return 0;
    }

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
                        .comparing((PlayerRatingDetails p) -> !p.getPlayer().getRealTeam().equals(startingGk.getPlayer().getRealTeam()))
                        .thenComparing(Comparator.comparing(PlayerRatingDetails::getFantaRating).reversed()))
                .toList();

        List<PlayerRatingDetails> outfieldBench = remaining.stream()
                .filter(p -> p.getPlayer().getPosition() != Player.PositionEnum.POR)
                .sorted(Comparator
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