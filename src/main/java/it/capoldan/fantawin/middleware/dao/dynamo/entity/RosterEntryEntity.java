package it.capoldan.fantawin.middleware.dao.dynamo.entity;

import it.capoldan.fantawin.dto.Role;
import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Un giocatore presente nella rosa fantacalcistica.
 * Tabella: MyRoster (PK rosterId, SK: playerId).
 */
@DynamoDbBean
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class RosterEntryEntity {

    public static final String COL_PK = "rosterId";
    public static final String COL_SK = "playerId";
    private static final String COL_FANTASY_ROLE = "fantasyRole";

    @Getter(onMethod=@__({@DynamoDbPartitionKey, @DynamoDbAttribute(COL_PK)}))
    private String rosterId;

    @Getter(onMethod=@__({@DynamoDbSortKey, @DynamoDbAttribute(COL_SK)}))
    private String playerId;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_FANTASY_ROLE)}))
    private Role fantasyRole;
}