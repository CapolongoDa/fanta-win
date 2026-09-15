package it.capoldan.fantawin.middleware.dao.dynamo.entity;

import it.capoldan.fantawin.dto.Role;
import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Anagrafica statica di un giocatore reale. Tabella: Players (PK: playerId).
 */
@DynamoDbBean
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PlayerEntity {

    public static final String COL_PK = "playerId";
    private static final String COL_NAME = "name";
    private static final String COL_REAL_TEAM = "realTeam";
    private static final String COL_ROLE = "role";
    private static final String COL_ACTIVE = "active";
    private static final String COL_VERSION = "version";

    @Getter(onMethod=@__({@DynamoDbPartitionKey, @DynamoDbAttribute(COL_PK)}))
    private String playerId;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_NAME)}))
    private String name;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_REAL_TEAM)}))
    private String realTeam;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_ROLE)}))
    private Role role;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_ACTIVE)}))
    private boolean active;

    @Getter(onMethod=@__({@DynamoDbVersionAttribute, @DynamoDbAttribute(COL_VERSION)}))
    private Long version;
}