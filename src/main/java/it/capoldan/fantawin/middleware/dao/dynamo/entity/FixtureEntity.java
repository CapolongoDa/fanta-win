package it.capoldan.fantawin.middleware.dao.dynamo.entity;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Calendario/difficolta' match per una squadra reale in una giornata.
 * Tabella: Fixtures (PK: matchday, SK: realTeam).
 */
@DynamoDbBean
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FixtureEntity {

    public static final String COL_PK = "matchday";
    public static final String COL_SK = "realTeam";
    private static final String COL_OPPONENT_TEAM = "opponentTeam";
    private static final String COL_HOME = "home";
    private static final String COL_MATCH_DIFFICULTY = "matchDifficulty";
    private static final String COL_EUROPEAN_CUP_BEFORE = "europeanCupBefore";
    private static final String COL_EUROPEAN_CUP_AFTER = "europeanCupAfter";
    private static final String COL_MIDWEEK_ROUND_BEFORE = "midweekRoundBefore";
    private static final String COL_MIDWEEK_ROUND_AFTER = "midweekRoundAfter";

    @Getter(onMethod=@__({@DynamoDbPartitionKey, @DynamoDbAttribute(COL_PK)}))
    private Integer matchday;

    @Getter(onMethod=@__({@DynamoDbSortKey, @DynamoDbAttribute(COL_SK)}))
    private String realTeam;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_OPPONENT_TEAM)}))
    private String opponentTeam;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_HOME)}))
    private boolean home;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_MATCH_DIFFICULTY)}))
    private Double matchDifficulty;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_EUROPEAN_CUP_BEFORE)}))
    private boolean europeanCupBefore;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_EUROPEAN_CUP_AFTER)}))
    private boolean europeanCupAfter;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_MIDWEEK_ROUND_BEFORE)}))
    private boolean midweekRoundBefore;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_MIDWEEK_ROUND_AFTER)}))
    private boolean midweekRoundAfter;
}