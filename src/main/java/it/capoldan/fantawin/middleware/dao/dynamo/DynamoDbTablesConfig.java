package it.capoldan.fantawin.middleware.dao.dynamo;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/**
 * Espone ogni tabella DynamoDB come bean Spring asincrono, così i repository
 * possono dichiarare una dipendenza tipizzata (es. DynamoDbAsyncTable<PlayerEntity>)
 * senza ricostruire lo schema ad ogni utilizzo.
 *
 * Tutte le operazioni sulle tabelle vengono eseguite tramite
 * DynamoDbEnhancedAsyncClient e possono quindi essere integrate
 * direttamente con Mono e Flux di Project Reactor.
 */
@Configuration
public class DynamoDbTablesConfig {

    @Bean
    public DynamoDbAsyncTable<PlayerEntity> playerTable(
            DynamoDbEnhancedAsyncClient client,
            FantaWinConfigs props) {

        return client.table(
                props.getDao().getPlayersTableName(),
                TableSchema.fromBean(PlayerEntity.class)
        );
    }

    @Bean
    public DynamoDbAsyncTable<PlayerMatchStatEntity> playerMatchStatTable(
            DynamoDbEnhancedAsyncClient client,
            FantaWinConfigs props) {

        return client.table(
                props.getDao().getPlayerMatchStatsTableName(),
                TableSchema.fromBean(PlayerMatchStatEntity.class)
        );
    }

    @Bean
    public DynamoDbAsyncTable<FixtureEntity> fixtureTable(
            DynamoDbEnhancedAsyncClient client,
            FantaWinConfigs props) {

        return client.table(
                props.getDao().getFixturesTableName(),
                TableSchema.fromBean(FixtureEntity.class)
        );
    }

    @Bean
    public DynamoDbAsyncTable<AvailabilityReportEntity> availabilityReportTable(
            DynamoDbEnhancedAsyncClient client,
            FantaWinConfigs props) {

        return client.table(
                props.getDao().getAvailabilityReportsTableName(),
                TableSchema.fromBean(AvailabilityReportEntity.class)
        );
    }

    @Bean
    public DynamoDbAsyncTable<RosterEntity> rosterTable(
            DynamoDbEnhancedAsyncClient client,
            FantaWinConfigs props) {

        return client.table(
                props.getDao().getRosterTableName(),
                TableSchema.fromBean(RosterEntity.class)
        );
    }

    @Bean
    public DynamoDbAsyncTable<PlayerCatalogEntity> playerCatalogTable(
            DynamoDbEnhancedAsyncClient client,
            FantaWinConfigs props) {

        return client.table(
                props.getDao().getPlayerCatalogTableName(),
                TableSchema.fromBean(PlayerCatalogEntity.class)
        );
    }
}