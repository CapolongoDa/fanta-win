package it.capoldan.fantawin.middleware.dao.dynamo.entity;
import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Statistiche di un giocatore per una singola giornata gia' disputata.
 * Tabella: PlayerMatchStats (PK: playerId, SK: matchday).
 */
@DynamoDbBean
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PlayerMatchStatEntity {

    public static final String COL_PK = "playerId";
    public static final String COL_SK = "matchday";
    private static final String COL_SEASON = "season";
    private static final String COL_OPPONENT_TEAM = "opponentTeam";
    private static final String COL_HOME = "home";
    private static final String COL_VOTO = "voto";
    private static final String COL_FANTAVOTO = "fantavoto";
    private static final String COL_GOL = "gol";
    private static final String COL_ASSIST = "assist";
    private static final String COL_AMMONIZIONI = "ammonizioni";
    private static final String COL_ESPULSIONI = "espulsioni";
    private static final String COL_XG = "xg";
    private static final String COL_XA = "xa";

    @Getter(onMethod=@__({@DynamoDbPartitionKey, @DynamoDbAttribute(COL_PK)}))
    private String playerId;

    @Getter(onMethod=@__({@DynamoDbSortKey, @DynamoDbAttribute(COL_SK)}))
    private Integer matchday;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_SEASON)}))
    private String season;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_OPPONENT_TEAM)}))
    private String opponentTeam;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_HOME)}))
    private boolean home;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_VOTO)}))
    private Double voto;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_FANTAVOTO)}))
    private Double fantavoto;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_GOL)}))
    private Integer gol;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_ASSIST)}))
    private Integer assist;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_AMMONIZIONI)}))
    private Integer ammonizioni;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_ESPULSIONI)}))
    private Integer espulsioni;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_XG)}))
    private Double xg;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_XA)}))
    private Double xa;
}
