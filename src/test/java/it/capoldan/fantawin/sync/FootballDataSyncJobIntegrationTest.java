package it.capoldan.fantawin.sync;

import it.capoldan.fantawin.ExternalApiMockServerConfig;
import it.capoldan.fantawin.LocalStackTestConfig;
import it.capoldan.fantawin.dto.FixtureDto;
import it.capoldan.fantawin.dto.PlayerDto;
import it.capoldan.fantawin.dto.Role;
import it.capoldan.fantawin.middleware.dao.dynamo.FixtureDao;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerDao;
import it.capoldan.fantawin.service.FootballDataSyncJob;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Verifica che {@link FootballDataSyncJob} scriva correttamente Fixtures a partire dalla risposta
 * di Football-Data.org, SENZA bisogno di rete reale: le chiamate sono intercettate da un MockServer
 * locale ({@link ExternalApiMockServerConfig}). A differenza di LineupPredictionIntegrationTest
 * (che chiama davvero football-data.org e viene saltato senza connessione/VPN che blocca), questo
 * test gira sempre, ovunque - e' il modo giusto per verificare la logica di sync quando la rete
 * verso i provider esterni non e' disponibile (VPN aziendali, reti ristrette, CI).
 *
 * Nota tempistiche: FootballDataSyncJob serializza le due chiamate (FINISHED/SCHEDULED) per
 * rispettare il rate limit del piano free di Football-Data.org (~6.5s di pausa) e applica la stessa
 * pausa dopo ogni squadra: anche con una sola squadra come qui, il test impiega ~13-15 secondi per
 * via di queste pause reali del job, non per lentezza del MockServer.
 */
@Slf4j
@SpringBootTest
@Import({LocalStackTestConfig.class, ExternalApiMockServerConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FootballDataSyncJobIntegrationTest {

    private static final Integer TEAM_ID = 108;
    private static final String REAL_TEAM_NAME = "Milan";
    private static final int EXPECTED_MATCHDAY = 5;

    @Autowired
    private PlayerDao playerDao;
    @Autowired
    private FixtureDao fixtureDao;
    @Autowired
    private FootballDataSyncJob footballDataSyncJob;

    @BeforeAll
    static void createTables() {
        DynamoDbClient client = DynamoDbClient.builder()
                .endpointOverride(LocalStackTestConfig.LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                .region(Region.of(LocalStackTestConfig.LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LocalStackTestConfig.LOCALSTACK.getAccessKey(), LocalStackTestConfig.LOCALSTACK.getSecretKey())))
                .build();
        createTable(client, "Players", "playerId", ScalarAttributeType.S, null, null);
        createTable(client, "Fixtures", "matchday", ScalarAttributeType.N, "realTeam", ScalarAttributeType.S);
    }

    private static void createTable(DynamoDbClient client, String tableName, String pk, ScalarAttributeType pkType,
                                     String sk, ScalarAttributeType skType) {
        List<AttributeDefinition> attrs = sk == null
                ? List.of(AttributeDefinition.builder().attributeName(pk).attributeType(pkType).build())
                : List.of(
                        AttributeDefinition.builder().attributeName(pk).attributeType(pkType).build(),
                        AttributeDefinition.builder().attributeName(sk).attributeType(skType).build());
        List<KeySchemaElement> keys = sk == null
                ? List.of(KeySchemaElement.builder().attributeName(pk).keyType(KeyType.HASH).build())
                : List.of(
                        KeySchemaElement.builder().attributeName(pk).keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName(sk).keyType(KeyType.RANGE).build());
        client.createTable(CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(attrs)
                .keySchema(keys)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
    }

    @AfterEach
    void resetMockServer() {
        ExternalApiMockServerConfig.MOCK_SERVER.reset();
    }

    @Test
    void sincronizzaFixtureDalMockDiFootballData() {
        playerDao.save(PlayerDto.builder()
                        .playerId("test-maignan")
                        .name("Maignan")
                        .realTeam(REAL_TEAM_NAME)
                        .role(Role.POR)
                        .active(true)
                        .build())
                .block(Duration.ofSeconds(10));

        stubCompetitionTeams();
        stubTeamMatches("FINISHED", emptyMatchesJson());
        stubTeamMatches("SCHEDULED", scheduledMatchJson());

        footballDataSyncJob.syncAllTeams();

        FixtureDto fixture = fixtureDao.getByMatchdayAndTeam(EXPECTED_MATCHDAY, REAL_TEAM_NAME)
                .block(Duration.ofSeconds(10));

        assertThat(fixture)
                .as("FootballDataSyncJob non ha scritto nessuna Fixture per %s/giornata %d",
                        REAL_TEAM_NAME, EXPECTED_MATCHDAY)
                .isNotNull();
        assertThat(fixture.getOpponentTeam()).isEqualTo("AS Roma");
        assertThat(fixture.isHome()).isTrue();
        assertThat(fixture.isEuropeanCupBefore()).isFalse();
        assertThat(fixture.isEuropeanCupAfter()).isFalse();
        assertThat(fixture.isMidweekRoundBefore()).isFalse();
        assertThat(fixture.isMidweekRoundAfter()).isFalse();

        log.info("Fixture scritta correttamente dal mock di Football-Data.org: {}", fixture);
    }

    private void stubCompetitionTeams() {
        String body = """
                {"teams":[{"id":%d,"name":"AC Milan","shortName":"%s","tla":"MIL"}]}
                """.formatted(TEAM_ID, REAL_TEAM_NAME);
        ExternalApiMockServerConfig.MOCK_SERVER.when(
                request().withMethod("GET").withPath("/competitions/SA/teams")
        ).respond(response().withStatusCode(200).withHeader("Content-Type", "application/json").withBody(body));
    }

    private void stubTeamMatches(String status, String body) {
        ExternalApiMockServerConfig.MOCK_SERVER.when(
                request().withMethod("GET")
                        .withPath("/teams/" + TEAM_ID + "/matches")
                        .withQueryStringParameter("status", status)
        ).respond(response().withStatusCode(200).withHeader("Content-Type", "application/json").withBody(body));
    }

    private static String emptyMatchesJson() {
        return "{\"matches\":[]}";
    }

    private static String scheduledMatchJson() {
        String utcDate = Instant.now().plus(Duration.ofDays(3)).toString();
        return """
                {"matches":[{"id":1,"utcDate":"%s","status":"SCHEDULED","matchday":%d,
                "competition":{"id":2019,"name":"Serie A","code":"SA"},
                "homeTeam":{"id":%d,"name":"AC Milan"},
                "awayTeam":{"id":109,"name":"AS Roma"}}]}
                """.formatted(utcDate, EXPECTED_MATCHDAY, TEAM_ID);
    }
}
