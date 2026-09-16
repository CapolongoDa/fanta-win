package it.capoldan.fantawin.prediction;

import it.capoldan.fantawin.dto.*;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.LineupRequest;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.LineupResponse;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.PlayerRatingDetails;
import it.capoldan.fantawin.middleware.dao.dynamo.*;
import it.capoldan.fantawin.service.FootballDataSyncJob;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test di integrazione end-to-end della pipeline di calcolo formazione: DynamoDB reale (via
 * Testcontainers/LocalStack, non mock), roster/statistiche/disponibilita' seminati con dati REALI
 * della rosa di Daniele (AS Junior.) e delle giornate 2-4 di Serie A 2026/27 (fonte: fantacalcio.it,
 * vedi commenti sui singoli dati sotto), prossimo avversario (giornata 5) recuperato dal vivo
 * chiamando il vero FootballDataSyncJob -> FootballDataClient -> football-data.org (non mockato).
 *
 * PREREQUISITI PER ESEGUIRLO:
 * 1) Docker in esecuzione in locale (Testcontainers avvia un container LocalStack per DynamoDB).
 * 2) Variabile d'ambiente FOOTBALL_DATA_API_KEY valorizzata con un token valido di football-data.org
 *    (nel progetto e' gia' configurata nella run configuration IntelliJ "FantaWinApplication" - se lanci
 *    il test da li' o esporti la stessa variabile in shell prima di "mvn test", il test gira per intero).
 *    SENZA questa variabile il test viene SALTATO automaticamente (non fallisce): sia questa sandbox
 *    cloud sia (in alcuni casi) reti aziendali bloccano le chiamate verso football-data.org, quindi il
 *    test e' pensato per essere eseguito da un ambiente con accesso di rete libero (es. il tuo Mac).
 *
 * NOTA SULLA GIORNATA: al momento in cui e' stato scritto questo test, la prossima giornata di Serie A
 * e' la 5 (18-20 settembre 2026). Se lo esegui in una settimana successiva, aggiorna MATCHDAY_UNDER_TEST
 * di conseguenza (il test fallisce con un messaggio esplicito se football-data.org non ha piu' quella
 * giornata come "prossima" per le squadre coinvolte, cosi' non passa silenziosamente su dati vecchi).
 */
@Slf4j
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT30S")
@EnabledIfEnvironmentVariable(named = "FOOTBALL_DATA_API_KEY", matches = ".+")
class LineupPredictionIntegrationTest {

    private static final int MATCHDAY_UNDER_TEST = 5;
    private static final String ROSTER_ID = "as-junior";

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8.1"))
            .withServices(LocalStackContainer.Service.DYNAMODB);

    @DynamicPropertySource
    static void awsProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.endpoint-url", () -> localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString());
        registry.add("aws.region-code", () -> localstack.getRegion());
        registry.add("aws.access-key-id", () -> localstack.getAccessKey());
        registry.add("aws.secret-access-key", () -> localstack.getSecretKey());
        // Disattiva l'auto-config spring-cloud-aws (non usata dai nostri DAO, ma per sicurezza
        // evitiamo che provi a raggiungere l'endpoint di default durante l'avvio del contesto).
        registry.add("spring.cloud.aws.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString());
        registry.add("spring.cloud.aws.region.static", () -> localstack.getRegion());
    }

    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private PlayerDao playerDao;
    @Autowired
    private RosterDao rosterDao;
    @Autowired
    private PlayerMatchStatDao playerMatchStatDao;
    @Autowired
    private AvailabilityReportDao availabilityReportDao;
    @Autowired
    private FixtureDao fixtureDao;
    @Autowired
    private FootballDataSyncJob footballDataSyncJob;

    @BeforeAll
    static void createTables() {
        DynamoDbClient client = DynamoDbClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();

        createTable(client, "Players", "playerId", ScalarAttributeType.S, null, null);
        createTable(client, "PlayerMatchStats", "playerId", ScalarAttributeType.S, "matchday", ScalarAttributeType.N);
        createTable(client, "Fixtures", "matchday", ScalarAttributeType.N, "realTeam", ScalarAttributeType.S);
        createTable(client, "AvailabilityReports", "playerId", ScalarAttributeType.S, null, null);
        createTable(client, "Roster", "rosterId", ScalarAttributeType.S, null, null);
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

    @Test
    void calcolaFormazioneOttimaleConDatiReali() {
        seedPlayersAndRoster();
        seedRecentMatchStats();
        seedAvailability();

        // Sincronizzazione REALE: chiama football-data.org (FootballDataTeamIdResolver +
        // FootballDataClient) per risolvere i teamId dai nomi in anagrafica e scrivere in Fixtures
        // il vero prossimo avversario di ogni squadra reale coinvolta, con i flag turnover/coppe.
        footballDataSyncJob.syncAllTeams();

        List<FixtureDto> syncedFixtures = fixtureDao.findByMatchday(MATCHDAY_UNDER_TEST).collectList()
                .block(Duration.ofSeconds(30));
        assertThat(syncedFixtures)
                .as("La sync da football-data.org non ha trovato nessuna fixture per la giornata %d: " +
                        "se e' gia' stata giocata, aggiorna MATCHDAY_UNDER_TEST alla giornata corrente", MATCHDAY_UNDER_TEST)
                .isNotEmpty();
        log.info("Fixture sincronizzate da football-data.org per la giornata {}: {}", MATCHDAY_UNDER_TEST, syncedFixtures);

        LineupRequest request = new LineupRequest()
                .matchday(MATCHDAY_UNDER_TEST)
                .modifierActive(true);

        LineupResponse response = webTestClient.post()
                .uri("/fanta-private/predict/lineup/{rosterId}", ROSTER_ID)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LineupResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.getWinningFormation()).isNotBlank();
        assertThat(response.getStartingEleven()).hasSize(11);
        assertThat(response.getBench()).isNotEmpty();

        List<String> starterIds = response.getStartingEleven().stream()
                .map(p -> p.getPlayer().getId()).toList();
        List<String> benchIds = response.getBench().stream()
                .map(p -> p.getPlayer().getId()).toList();

        // Modulo 3: McTominay e Doekhi sono INFORTUNATO -> rating forzato a 0 -> esclusi
        // tassativamente sia dai titolari che dalla panchina.
        assertThat(starterIds).doesNotContain("mctominay", "doekhi");
        assertThat(benchIds).doesNotContain("mctominay", "doekhi");

        // Nessun titolare puo' avere fantaRating 0 (implicito nel punto sopra, ma verificato
        // esplicitamente sul valore restituito, non solo sull'assenza dalla lista).
        assertThat(response.getStartingEleven())
                .allSatisfy(p -> assertThat(p.getFantaRating()).isGreaterThan(0.0f));

        log.info("=== FORMAZIONE CALCOLATA (giornata {}) ===", MATCHDAY_UNDER_TEST);
        log.info("Modulo vincente: {} (punteggio totale: {}, bonus modificatore difesa: {})",
                response.getWinningFormation(), response.getTotalExpectedScore(), response.getDefenseModifierBonus());
        log.info("--- Titolari ---");
        for (PlayerRatingDetails p : response.getStartingEleven()) {
            log.info("{} {} ({}) - FantaRating {}", p.getPlayer().getPosition(), p.getPlayer().getName(),
                    p.getPlayer().getRealTeam(), p.getFantaRating());
        }
        log.info("--- Panchina ---");
        for (PlayerRatingDetails p : response.getBench()) {
            log.info("{} {} ({}) - FantaRating {}", p.getPlayer().getPosition(), p.getPlayer().getName(),
                    p.getPlayer().getRealTeam(), p.getFantaRating());
        }
    }

    // ---------------------------------------------------------------------------------------
    // Seed dati - rosa reale "AS Junior." di Daniele
    // ---------------------------------------------------------------------------------------

    private void seedPlayersAndRoster() {
        List<PlayerDto> players = List.of(
                player("okoye", "Okoye", "Udinese", Role.POR),
                player("perri", "Perri", "Torino", Role.POR),
                player("skorupski", "Skorupski", "Bologna", Role.POR),
                player("bremer", "Bremer", "Juventus", Role.DIF),
                player("comuzzo", "Comuzzo", "Torino", Role.DIF),
                player("dilorenzo", "Di Lorenzo", "Napoli", Role.DIF),
                player("doekhi", "Doekhi", "Lazio", Role.DIF),
                player("molina", "Molina", "Roma", Role.DIF),
                player("ndicka", "Ndicka", "Roma", Role.DIF),
                player("ostigard", "Ostigard", "Genoa", Role.DIF),
                player("spinazzola", "Spinazzola", "Napoli", Role.DIF),
                player("barella", "Barella", "Inter", Role.CEN),
                player("ederson", "Ederson", "Atalanta", Role.CEN),
                player("ekkelenkamp", "Ekkelenkamp", "Udinese", Role.CEN),
                player("kone", "Kone", "Roma", Role.CEN),
                player("mctominay", "McTominay", "Napoli", Role.CEN),
                player("modric", "Modric", "Milan", Role.CEN),
                player("moreira", "Moreira", "Milan", Role.CEN),
                player("vlasic", "Vlasic", "Torino", Role.CEN),
                player("davis", "Davis", "Udinese", Role.ATT),
                player("deketelaere", "De Ketelaere", "Atalanta", Role.ATT),
                player("lauriente", "Laurientè", "Sassuolo", Role.ATT),
                player("pinamonti", "Pinamonti", "Lazio", Role.ATT),
                player("simeone", "Simeone", "Torino", Role.ATT),
                player("soule", "Soulè", "Roma", Role.ATT)
        );

        Flux.fromIterable(players).flatMap(playerDao::save).blockLast(Duration.ofSeconds(30));

        RosterDto roster = RosterDto.builder()
                .rosterId(ROSTER_ID)
                .teamName("AS Junior.")
                .players(players.stream()
                        .map(p -> RosterPlayerDto.builder().playerId(p.getPlayerId()).fantasyRole(p.getRole()).build())
                        .toList())
                .build();
        rosterDao.save(roster).block(Duration.ofSeconds(30));
    }

    private static PlayerDto player(String id, String name, String realTeam, Role role) {
        return PlayerDto.builder().playerId(id).name(name).realTeam(realTeam).role(role).active(true).build();
    }

    /**
     * Voti reali giornate 2-4 stagione 2026/27, fonte fantacalcio.it (pagine /voti per singola
     * partita, es. fantacalcio.it/serie-a/calendario/4/2026-27/inter-udinese/17988/voti). Copertura
     * parziale per alcuni giocatori (rotazioni/panchina nelle giornate non trovate) - riflette i dati
     * effettivamente recuperati, non e' stato inventato nulla per riempire i buchi. voto e fantavoto
     * sono valorizzati con lo stesso numero (il fantavoto con bonus/malus scomposto non era distinguibile
     * dalla pagina); xg/xa non disponibili su fantacalcio.it e lasciati null.
     */
    private void seedRecentMatchStats() {
        List<PlayerMatchStatDto> stats = List.of(
                // Giornata 4
                stat("barella", 4, "Inter", true, 7.5),
                stat("davis", 4, "Udinese", false, 6.5),
                stat("perri", 4, "Torino", true, 5.5),
                stat("comuzzo", 4, "Torino", true, 5.5),
                stat("vlasic", 4, "Torino", true, 5.5),
                stat("simeone", 4, "Torino", true, 8.5),
                stat("molina", 4, "Roma", false, 6.0),
                stat("kone", 4, "Roma", false, 6.0),
                stat("dilorenzo", 4, "Napoli", true, 9.0),
                stat("spinazzola", 4, "Napoli", true, 6.5),
                stat("pinamonti", 4, "Lazio", true, 8.5),
                stat("modric", 4, "Milan", false, 9.5),
                stat("ederson", 4, "Atalanta", true, 5.5),
                stat("deketelaere", 4, "Atalanta", true, 5.0),
                stat("lauriente", 4, "Sassuolo", true, 6.5),
                stat("bremer", 4, "Juventus", false, 5.0),
                stat("ostigard", 4, "Genoa", true, 6.0),
                // Giornata 3
                stat("ndicka", 3, "Atalanta", true, 5.5),
                stat("molina", 3, "Atalanta", true, 6.0),
                stat("kone", 3, "Atalanta", true, 6.0),
                stat("soule", 3, "Atalanta", true, 8.5),
                stat("ederson", 3, "Roma", false, 7.0),
                stat("deketelaere", 3, "Roma", false, 6.0),
                stat("skorupski", 3, "Sassuolo", true, 7.5),
                // Giornata 2
                stat("okoye", 2, "Monza", false, 7.0),
                stat("ekkelenkamp", 2, "Monza", false, 9.5),
                stat("davis", 2, "Monza", false, 6.5)
        );
        Flux.fromIterable(stats).flatMap(playerMatchStatDao::save).blockLast(Duration.ofSeconds(30));
    }

    private static PlayerMatchStatDto stat(String playerId, int matchday, String opponentTeam, boolean home, double voto) {
        return PlayerMatchStatDto.builder()
                .playerId(playerId)
                .matchDay(matchday)
                .season("2026-27")
                .opponentTeam(opponentTeam)
                .home(home)
                .voto(voto)
                .fantavoto(voto)
                .build();
    }

    /**
     * Stato disponibilita' come confermato da Daniele in chat (14/09/2026), con un'eccezione:
     * Ndicka e' stato riclassificato IN_DUBBIO (non OK come dichiarato inizialmente) sulla base di
     * una notizia reale trovata su fantacalcio.it/Sky Sport ("ancora alle prese con noie fisiche",
     * escluso dai titolari in Torino-Roma) - vedi la conversazione per il dettaglio.
     */
    private void seedAvailability() {
        List<AvailabilityReportDto> reports = List.of(
                AvailabilityReportDto.builder().playerId("mctominay").status(AvailabilityStatus.INFORTUNATO).startingProbability(0.0).build(),
                AvailabilityReportDto.builder().playerId("doekhi").status(AvailabilityStatus.INFORTUNATO).startingProbability(0.0).build(),
                AvailabilityReportDto.builder().playerId("molina").status(AvailabilityStatus.BALLOTTAGGIO).startingProbability(65.0).build(),
                AvailabilityReportDto.builder().playerId("pinamonti").status(AvailabilityStatus.BALLOTTAGGIO).startingProbability(65.0).build(),
                AvailabilityReportDto.builder().playerId("ndicka").status(AvailabilityStatus.IN_DUBBIO).build()
        );
        Flux.fromIterable(reports).flatMap(availabilityReportDao::save).blockLast(Duration.ofSeconds(30));
    }
}
