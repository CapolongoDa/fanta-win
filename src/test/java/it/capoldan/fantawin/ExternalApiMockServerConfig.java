package it.capoldan.fantawin;

import lombok.extern.slf4j.Slf4j;
import org.mockserver.integration.ClientAndServer;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * Configurazione condivisa di un MockServer per simulare le risposte di Football-Data.org e
 * SportMonks nei test di integrazione delle sync, sul modello di {@link LocalStackTestConfig}:
 * il server viene avviato in un blocco static PRIMA che il contesto Spring venga costruito, e la
 * sua URL viene iniettata via System.setProperty su fantawin.football-data-base-url e
 * fantawin.sportmonks-base-url. Le System properties hanno precedenza sui valori reali gia'
 * presenti in config/application.properties (stessa tecnica gia' usata da LocalStackTestConfig
 * per sovrascrivere aws.endpoint-url), quindi questi test NON toccano mai football-data.org o
 * SportMonks veri: girano identici anche dietro una VPN/rete aziendale che li blocca.
 *
 * Un solo MockServer serve entrambi i provider: i path non si sovrappongono mai (Football-Data.org
 * usa /competitions/... e /teams/{id}/matches, SportMonks usa /teams/search/..., /fixtures/between/...
 * e /rounds/seasons/...), quindi non serve un'istanza separata per ciascuno.
 *
 * Ogni classe di test che la importa deve richiamare MOCK_SERVER.reset() in un @AfterEach, per non
 * far trapelare le expectation impostate da un test in quello successivo (il MockServer e' static
 * e condiviso per l'intera JVM di test, come il container LocalStack).
 */
@Slf4j
@TestConfiguration
public class ExternalApiMockServerConfig {

    public static final ClientAndServer MOCK_SERVER = ClientAndServer.startClientAndServer(0);

    static {
        String baseUrl = "http://localhost:" + MOCK_SERVER.getLocalPort();
        System.setProperty("fantawin.football-data-base-url", baseUrl);
        System.setProperty("fantawin.sportmonks-base-url", baseUrl);
        log.info("MockServer avviato per i test di integrazione delle sync esterne: {}", baseUrl);
    }
}
