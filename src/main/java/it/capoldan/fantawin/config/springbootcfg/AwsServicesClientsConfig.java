package it.capoldan.fantawin.config.springbootcfg;

import it.capoldan.fantawin.config.aws.AwsConfigs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
@ConditionalOnProperty( name = "middleware.init.aws", havingValue = "true")
@Slf4j
public class AwsServicesClientsConfig {

    private final AwsConfigs props;

    public AwsServicesClientsConfig(AwsConfigs props) {
        this.props = props;
    }

    @Bean
    public DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient( DynamoDbAsyncClient baseAsyncClient) {
        return DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient( baseAsyncClient )
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient( DynamoDbClient baseClient ) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient( baseClient )
                .build();
    }

}
