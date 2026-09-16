package it.capoldan.fantawin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Configurazione condivisa del container LocalStack per i test di integrazione, sul modello
 * di it.pagopa.pn.workflowmanager.LocalStackTestConfig (pattern richiesto da Daniele).
 *
 * A differenza di @Testcontainers/@Container/@DynamicPropertySource (approccio precedente), qui
 * il container e' un campo static avviato in un blocco static {} e le proprieta' vengono passate
 * alla JVM con System.setProperty PRIMA che il contesto Spring venga costruito, cosi' come nel
 * riferimento. Le credenziali "finte" per LocalStack vengono lette da un file sul classpath
 * (src/test/resources/testcontainers/credentials) ed esposte via aws.sharedCredentialsFile: e'
 * la stessa proprieta' di sistema che AwsServicesClientsConfig.credentialsProvider() intercetta
 * automaticamente tramite DefaultCredentialsProvider quando le proprieta' custom
 * aws.access-key-id/aws.secret-access-key NON sono valorizzate (vedi config/application.properties
 * di test, che infatti non le imposta piu').
 *
 * NOTA IMPORTANTE: questa classe risolve SOLO come il container viene cablato al contesto Spring.
 * Se l'errore e' "Could not find a valid Docker environment", il problema e' a monte - Testcontainers
 * non riesce a parlare col Docker daemon - e si presenta identico sia con @Container sia con questo
 * pattern, perche' in entrambi i casi "new LocalStackContainer(...)" prova a risolvere l'ambiente
 * Docker nel costruttore, prima ancora di start(). Vedi il messaggio di Claude in chat per i controlli
 * da fare su Docker Desktop/Colima.
 */
@Slf4j
@TestConfiguration
public class LocalStackTestConfig {

    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:3.8.1");

    public static final LocalStackContainer LOCALSTACK = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.DYNAMODB);

    static {
        log.info("Avvio container LocalStack (DynamoDB) per i test di integrazione...");
        LOCALSTACK.start();

        System.setProperty("aws.endpoint-url",
                LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString());
        System.setProperty("aws.region-code", LOCALSTACK.getRegion());

        try {
            String credentialsFilePath = new ClassPathResource("testcontainers/credentials").getFile().getAbsolutePath();
            System.setProperty("aws.sharedCredentialsFile", credentialsFilePath);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Impossibile risolvere src/test/resources/testcontainers/credentials sul classpath", e);
        }

        log.info("LocalStack pronto: endpoint={}", LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.DYNAMODB));
    }
}
