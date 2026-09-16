package it.capoldan.fantawin.utils;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.dto.*;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.FantaRatingBreakdown;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.LineupRequest;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.Player;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.PlayerRatingDetails;
import it.capoldan.fantawin.mapper.RosterAggregationMapper;
import it.capoldan.fantawin.service.PlayerFormAggregationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
public class PlayerRatingCalculator {

    private final FantaWinConfigs fantaWinConfigs;

    public PlayerCalculation compute(PlayerDto player, AvailabilityReportDto availability,
                                     FixtureDto fixture, PlayerFormAggregationService.FormComponents form,
                                     LineupRequest request) {

        if (availability.getStatus() == AvailabilityStatus.INFORTUNATO
                || availability.getStatus() == AvailabilityStatus.SQUALIFICATO) {
            PlayerRatingDetails details = buildDetails(player, availability, fixture, form.recentMatches(), 0.0,
                    FantaRatingBreakdown.builder()
                            .baseFormScore(0.0f).historicalScore(0.0f).matchDifficultyXgScore(0.0f)
                            .midweekPenalty(0.0f).availabilityPenalty(0.0f).build());
            return new PlayerCalculation(details, form.recentPureVoteAverage());
        }

        double matchDifficultyXg = matchDifficultyComponent(fixture.getMatchDifficulty()) * 0.5 + form.xgXaScore() * 0.5;
        double baseRating = form.recentFormScore() * 0.4
                + form.historicalVsOpponentScore() * 0.3
                + matchDifficultyXg * 0.3;

        double midweekPenalty = 0.0;
        boolean hasMidweekOrEuropean = fixture.isEuropeanCupBefore() || fixture.isEuropeanCupAfter()
                || fixture.isMidweekRoundBefore() || fixture.isMidweekRoundAfter()
                || (request.getTeamsWithMidweekMatches() != null
                && request.getTeamsWithMidweekMatches().contains(player.getRealTeam()));
        if (hasMidweekOrEuropean) {
            boolean isBig = form.recentFormScore() >= fantaWinConfigs.getBigPlayerThreshold();
            midweekPenalty = isBig ? -1.5 : -2.5;
        }

        double availabilityPenalty = 0.0;
        Double startingProb = availability.getStartingProbability();
        if (startingProb != null && startingProb < 50.0) {
            availabilityPenalty = -2.0;
        } else if (startingProb != null && startingProb >= 55.0 && startingProb <= 75.0) {
            availabilityPenalty = -0.8;
        } else if (availability.getStatus() == AvailabilityStatus.IN_DUBBIO) {
            availabilityPenalty = -1.0;
        }

        double finalRating = Math.clamp(baseRating + midweekPenalty + availabilityPenalty, 0.0, 10.0);

        FantaRatingBreakdown breakdown = FantaRatingBreakdown.builder()
                .baseFormScore((float) (form.recentFormScore() * 0.4))
                .historicalScore((float) (form.historicalVsOpponentScore() * 0.3))
                .matchDifficultyXgScore((float) (matchDifficultyXg * 0.3))
                .midweekPenalty((float) midweekPenalty)
                .availabilityPenalty((float) availabilityPenalty)
                .build();

        PlayerRatingDetails details = buildDetails(player, availability, fixture, form.recentMatches(), finalRating, breakdown);
        return new PlayerCalculation(details, form.recentPureVoteAverage());
    }

    private double matchDifficultyComponent(Double matchDifficulty) {
        if (matchDifficulty == null) return 5.0;
        return Math.max(0.0, 10.0 - matchDifficulty);
    }

    private PlayerRatingDetails buildDetails(PlayerDto player, AvailabilityReportDto availability, FixtureDto fixture,
                                             List<PlayerMatchStatDto> recentMatches, double rating,
                                             FantaRatingBreakdown breakdown) {
        Player apiPlayer = RosterAggregationMapper.toApiPlayer(
                player, availability.getStatus(), availability.getStartingProbability(),
                fixture, recentMatches);

        return PlayerRatingDetails.builder()
                .player(apiPlayer)
                .fantaRating((float) rating)
                .fantaRatingBreakdown(breakdown)
                .build();
    }
}