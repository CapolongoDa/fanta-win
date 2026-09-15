package it.capoldan.fantawin.middleware.dao.dynamo.entity;

import it.capoldan.fantawin.dto.AvailabilityStatus;
import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

/**
 * Ultimo stato noto di disponibilita' di un giocatore.
 * Tabella: AvailabilityReports (PK: playerId).
 */
@DynamoDbBean
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AvailabilityReportEntity {

    public static final String COL_PK = "playerId";
    private static final String COL_STATUS = "status";
    private static final String COL_STARTING_PROBABILITY = "startingProbability";
    private static final String COL_NOTE = "note";
    private static final String COL_LAST_UPDATED = "lastUpdated";

    @Getter(onMethod=@__({@DynamoDbPartitionKey, @DynamoDbAttribute(COL_PK)}))
    private String playerId;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_STATUS)}))
    private AvailabilityStatus status;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_STARTING_PROBABILITY)}))
    private Double startingProbability;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_NOTE)}))
    private String note;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_LAST_UPDATED)}))
    private Instant lastUpdated;
}