package it.capoldan.fantawin.dao;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixtureDto {

    private Integer matchday;

    private String realTeam;

    private String opponentTeam;

    private boolean home;

    private Double matchDifficulty;

    private boolean europeanCupBefore;

    private boolean europeanCupAfter;

    private boolean midweekRoundBefore;

    private boolean midweekRoundAfter;
}