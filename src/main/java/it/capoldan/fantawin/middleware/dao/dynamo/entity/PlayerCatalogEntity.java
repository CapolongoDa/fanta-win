package it.capoldan.fantawin.middleware.dao.dynamo.entity;

import it.capoldan.fantawin.dto.Role;
import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Anagrafica completa dei calciatori di Serie A (quotazioni fantacalcio.it). Tabella: PlayerCatalog
 * (PK: catalogId, l'id numerico originale delle quotazioni). Popolata via import CSV
 * (PlayerCatalogImportService), usata in lettura da PlayerCatalogResolverService per risolvere
 * nome+ruolo -> playerId quando si aggiunge un giocatore alla rosa.
 */
@DynamoDbBean
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PlayerCatalogEntity {

    public static final String COL_PK = "catalogId";
    private static final String COL_NOME = "nome";
    private static final String COL_RUOLO = "ruolo";
    private static final String COL_SQUADRA = "squadra";

    @Getter(onMethod=@__({@DynamoDbPartitionKey, @DynamoDbAttribute(COL_PK)}))
    private String catalogId;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_NOME)}))
    private String nome;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_RUOLO)}))
    private Role ruolo;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_SQUADRA)}))
    private String squadra;
}
