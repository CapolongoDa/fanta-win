package it.capoldan.fantawin.middleware.dao.dynamo.entity;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.util.List;

/**
 * La rosa fantacalcistica dell'utente (la sua squadra).
 * Tabella: Roster (PK: rosterId). Contiene il nome squadra e la lista
 * dei giocatori posseduti.
 */
@DynamoDbBean
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class RosterEntity {

    public static final String COL_PK = "rosterId";
    private static final String COL_TEAM_NAME = "teamName";
    private static final String COL_PLAYERS = "players";
    private static final String COL_OWNER_ID = "ownerId";

    @Getter(onMethod=@__({@DynamoDbPartitionKey, @DynamoDbAttribute(COL_PK)}))
    private String rosterId;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_TEAM_NAME)}))
    private String teamName;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_PLAYERS)}))
    private List<RosterPlayerEntity> players;

    /** Sub Cognito dell'utente che ha creato questa rosa (null sulle rose create prima di attivare
     * Cognito: RosterService le tratta come "non reclamate", nessun controllo di ownership su di
     * loro finche' non viene esplicitamente assegnato un ownerId). */
    @Getter(onMethod=@__({@DynamoDbAttribute(COL_OWNER_ID)}))
    private String ownerId;
}