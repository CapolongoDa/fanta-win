package it.capoldan.fantawin.middleware.dao.dynamo.entity;

import it.capoldan.fantawin.dto.Role;
import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/** Singolo giocatore posseduto all'interno di una rosa (elemento della lista). */
@DynamoDbBean
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class RosterPlayerEntity {

    private static final String COL_PLAYER_ID = "playerId";
    private static final String COL_FANTASY_ROLE = "fantasyRole";

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_PLAYER_ID)}))
    private String playerId;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_FANTASY_ROLE)}))
    private Role fantasyRole;
}