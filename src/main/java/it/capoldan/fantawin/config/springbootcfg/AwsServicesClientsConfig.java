package it.capoldan.fantawin.config.springbootcfg;

import it.capoldan.fantawin.config.aws.AwsConfigs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
@Configuration
@ConditionalOnProperty(name = "middleware.init.aws", havingValue = "true")
@Slf4j
public class AwsServicesClientsConfig {

    private final AwsConfigs props;

    public AwsServicesClientsConfig(AwsConfigs props) {
        this.props = props;
    }

    @Bean
    public DynamoDbAsyncClient dynamoDbAsyncClient() {
        var builder = DynamoDbAsyncClient.builder()
                .region(Region.of(props.getRegionCode()))
                .credentialsProvider(credentialsProvider());
        if (StringUtils.hasText(props.getEndpointUrl())) {
            builder.endpointOverride(URI.create(props.getEndpointUrl()));
        }
        return builder.build();
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder()
                .region(Region.of(props.getRegionCode()))
                .credentialsProvider(credentialsProvider());
        if (StringUtils.hasText(props.getEndpointUrl())) {
            builder.endpointOverride(URI.create(props.getEndpointUrl()));
        }
        return builder.build();
    }

    private AwsCredentialsProvider credentialsProvider() {
        // Credenziali statiche solo se fornite (es. locale/CI); altrimenti catena di default
        // (IAM role su ECS, variabili d'ambiente, ~/.aws, ecc.)
        if (StringUtils.hasText(props.getAccessKeyId()) && StringUtils.hasText(props.getSecretAccessKey())) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.getAccessKeyId(), props.getSecretAccessKey()));
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient(DynamoDbAsyncClient baseAsyncClient) {
        return DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(baseAsyncClient)
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient baseClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(baseClient)
                .build();
    }
}