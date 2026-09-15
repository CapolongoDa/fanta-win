package it.capoldan.fantawin.middleware.dao.dynamo;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/**
 * Espone ogni tabella DynamoDB come bean Spring, cosi' i service possono
 * dichiarare una dipendenza tipizzata (es. DynamoDbTable<PlayerEntity>) invece
 * di ricostruire lo schema ad ogni utilizzo.
 */
@Configuration
public class DynamoDbTablesConfig {

    @Bean
    public DynamoDbTable<PlayerEntity> playerTable(DynamoDbEnhancedClient client, FantaWinConfigs props) {
        return client.table(props.getDao().getPlayersTableName(), TableSchema.fromBean(PlayerEntity.class));
    }

    @Bean
    public DynamoDbTable<PlayerMatchStatEntity> playerMatchStatTable(DynamoDbEnhancedClient client, FantaWinConfigs props) {
        return client.table(props.getDao().getPlayerMatchStatsTableName(), TableSchema.fromBean(PlayerMatchStatEntity.class));
    }

    @Bean
    public DynamoDbTable<FixtureEntity> fixtureTable(DynamoDbEnhancedClient client, FantaWinConfigs props) {
        return client.table(props.getDao().getFixturesTableName(), TableSchema.fromBean(FixtureEntity.class));
    }

    @Bean
    public DynamoDbTable<AvailabilityReportEntity> availabilityReportTable(DynamoDbEnhancedClient client, FantaWinConfigs props) {
        return client.table(props.getDao().getAvailabilityReportsTableName(), TableSchema.fromBean(AvailabilityReportEntity.class));
    }

    @Bean
    public DynamoDbTable<RosterEntryEntity> rosterEntryTable(DynamoDbEnhancedClient client, FantaWinConfigs props) {
        return client.table(props.getDao().getRosterTableName(), TableSchema.fromBean(RosterEntryEntity.class));
    }
}