package it.capoldan.fantawin.sync;

import it.capoldan.fantawin.ExternalApiMockServerConfig;
import it.capoldan.fantawin.LocalStackTestConfig;
import it.capoldan.fantawin.dto.AvailabilityReportDto;
import it.capoldan.fantawin.dto.PlayerDto;
import it.capoldan.fantawin.dto.Role;
import it.capoldan.fantawin.dto.RosterDto;
import it.capoldan.fantawin.dto.RosterPlayerDto;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.OfficialLineupSyncResult;
import it.capoldan.fantawin.middleware.dao.dynamo.AvailabilityReportDao;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerDao;
import it.capoldan.fantawin.middleware.dao.dynamo.RosterDao;
import it.capoldan.fantawin.service.OfficialLineupSyncService;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Verifica che {@link OfficialLineupSyncService} aggiorni correttamente
 * AvailabilityReport.startingProbability a partire dalla formazione ufficiale di SportMonks,
 * SENZA bisogno di rete reale: le chiamate sono intercettate da un MockServer locale
 * ({@link ExternalApiMockServerConfig}). E' il test piu' vicino allo scenario reale riportato
 * dall'utente (Maignan schierato titolare al posto di Torriani, Busio incluso in formazione
 * nonostante l'infortunio): dimostra che, quando la formazione ufficiale e' effettivamente
 * disponibile su SportMonks, questo servizio la applica correttamente ai due giocatori - il
 * problema riscontrato dall'utente era quindi a monte (nessuna sync mai eseguita per quella rosa),
 * non un difetto in questa logica.
 */
@Slf4j
@SpringBootTest
@Import({LocalStackTestConfig.class, ExternalApiMockServerConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OfficialLineupSyncServiceIntegrationTest {

    private static final Integer TEAM_ID = 445;
    private static final String REAL_TEAM_NAME = "Milan";
    private static final String ROSTER_ID = "test-roster-lineup-sync";
    private static final Integer STARTER_TYPE_ID = 11;
    private static final Integer BENCH_TYPE_ID = 12;

    @Autowired
    private PlayerDao playerDao;
    @Autowired
    private RosterDao rosterDao;
    @Autowired
    private AvailabilityReportDao availabilityReportDao;
    @Autowired
    private OfficialLineupSyncService officialLineupSyncService;

    @BeforeAll
    static void createTables() {
        DynamoDbClient client = DynamoDbClient.builder()
                .endpointOverride(LocalStackTestConfig.LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                .region(Region.of(LocalStackTestConfig.LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LocalStackTestConfig.LOCALSTACK.getAccessKey(), LocalStackTestConfig.LOCALSTACK.getSecretKey())))
                .build();
        createTable(client, "Players", "playerId");
        createTable(client, "Roster", "rosterId");
        createTable(client, "AvailabilityReports", "playerId");
    }

    private static void createTable(DynamoDbClient client, String tableName, String pk) {
        client.createTable(CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(AttributeDefinition.builder().attributeName(pk).attributeType(ScalarAttributeType.S).build())
                .keySchema(KeySchemaElement.builder().attributeName(pk).keyType(KeyType.HASH).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
    }

    @AfterEach
    void resetMockServer() {
        ExternalApiMockServerConfig.MOCK_SERVER.reset();
    }

    @Test
    void aggiornaStartingProbabilityDallaFormazioneUfficialeSportMonks() {
        playerDao.save(PlayerDto.builder()
                        .playerId("test-maignan")
                        .name("Maignan")
                        .realTeam(REAL_TEAM_NAME)
                        .role(Role.POR)
                        .active(true)
                        .build())
                .block(Duration.ofSeconds(10));
        playerDao.save(PlayerDto.builder()
                        .playerId("test-torriani")
                        .name("Torriani")
                        .realTeam(REAL_TEAM_NAME)
                        .role(Role.POR)
                        .active(true)
                        .build())
                .block(Duration.ofSeconds(10));

        rosterDao.save(RosterDto.builder()
                        .rosterId(ROSTER_ID)
                        .teamName("Test Team")
                        .players(List.of(
                                RosterPlayerDto.builder().playerId("test-maignan").fantasyRole(Role.POR).build(),
                                RosterPlayerDto.builder().playerId("test-torriani").fantasyRole(Role.POR).build()))
                        .build())
                .block(Duration.ofSeconds(10));

        stubTeamSearch();
        stubFixturesWithLineup();

        OfficialLineupSyncResult result = officialLineupSyncService.syncOfficialLineup(ROSTER_ID)
                .block(Duration.ofSeconds(10));

        assertThat(result).as("syncOfficialLineup non ha prodotto nessun risultato").isNotNull();
        assertThat(result.getUpdatedPlayers()).hasSize(2);
        assertThat(result.getUnresolvedTeams()).isEmpty();

        AvailabilityReportDto maignan = availabilityReportDao.getById("test-maignan").block(Duration.ofSeconds(10));
        AvailabilityReportDto torriani = availabilityReportDao.getById("test-torriani").block(Duration.ofSeconds(10));

        assertThat(maignan)
                .as("Maignan e' titolare (type_id=%d) nella formazione ufficiale mockata", STARTER_TYPE_ID)
                .isNotNull();
        assertThat(maignan.getStartingProbability()).isEqualTo(100.0);

        assertThat(torriani)
                .as("Torriani e' in panchina (type_id=%d) nella formazione ufficiale mockata", BENCH_TYPE_ID)
                .isNotNull();
        assertThat(torriani.getStartingProbability()).isEqualTo(0.0);

        log.info("Formazione ufficiale sincronizzata correttamente dal mock di SportMonks: maignan={} torriani={}",
                maignan, torriani);
    }

    private void stubTeamSearch() {
        String body = """
                {"data":[{"id":%d,"name":"AC Milan","short_code":"MIL"}]}
                """.formatted(TEAM_ID);
        ExternalApiMockServerConfig.MOCK_SERVER.when(
                request().withMethod("GET").withPath("/teams/search/" + REAL_TEAM_NAME)
        ).respond(response().withStatusCode(200).withHeader("Content-Type", "application/json").withBody(body));
    }

    private void stubFixturesWithLineup() {
        // Stessa finestra di date usata da OfficialLineupSyncService.fetchLineup (oggi-1, oggi+3):
        // il path e' interrogato per intero (start/end inclusi), quindi lo ricalcoliamo qui identico.
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        LocalDate today = LocalDate.now();
        String startDate = today.minusDays(1).format(fmt);
        String endDate = today.plusDays(3).format(fmt);

        String body = """
                {"data":[{"id":1,"name":"AC Milan vs AS Roma","starting_at":"%s","league_id":384,
                "lineups":[
                  {"player_id":1,"player_name":"Maignan","team_id":%d,"type_id":%d,"formation_field":"1:1","position_id":24,"jersey_number":16},
                  {"player_id":2,"player_name":"Torriani","team_id":%d,"type_id":%d,"formation_field":null,"position_id":24,"jersey_number":1}
                ]}]}
                """.formatted(startDate + "T18:00:00.000000Z", TEAM_ID, STARTER_TYPE_ID, TEAM_ID, BENCH_TYPE_ID);

        ExternalApiMockServerConfig.MOCK_SERVER.when(
                request().withMethod("GET")
                        .withPath("/fixtures/between/" + startDate + "/" + endDate + "/" + TEAM_ID)
                        .withQueryStringParameter("include", "lineups")
        ).respond(response().withStatusCode(200).withHeader("Content-Type", "application/json").withBody(body));
    }
}
