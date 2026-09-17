package it.capoldan.fantawin.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties( prefix = "fantawin")
@Validated
@Data
@Import({SharedAutoConfiguration.class})
@Slf4j
public class FantaWinConfigs {
    private Dao dao;
    private String footballDataBaseUrl;
    // Esclusa dal toString generato da @Data: init() logga "this" all'avvio,
    // e la api key non deve mai finire in chiaro nei log applicativi.
    @lombok.ToString.Exclude
    private String footballDataApiKey;
    private String sportmonksBaseUrl;
    @lombok.ToString.Exclude
    private String sportmonksApiKey;
    // Id numerico della lega Serie A su SportMonks: verificalo nella tua dashboard/documentazione
    // SportMonks (varia in base al piano/leghe incluse nel tuo account). Se lasciato vuoto,
    // OfficialLineupSyncService non filtra per lega e si aspetta un solo fixture nella finestra di
    // date interrogata per squadra: se ne trova piu' di uno salta la squadra con un warning invece
    // di indovinare quale sia quello giusto.
    private Integer sportmonksSerieALeagueId;
    private double defaultMatchDifficulty;
    private double bigPlayerThreshold;
    // Peso del voto puro (senza bonus/malus) nella componente stato di forma recente del FantaRating,
    // per centrocampisti/attaccanti: 0.0 = solo fantavoto (comportamento originale), 1.0 = solo voto
    // puro. Il fantavoto da solo premia i bonus fortunati (gol/assist) e puo penalizzare un
    // giocatore che gioca bene ma non ha ancora concretizzato in bonus (default 0.8: 80% voto puro,
    // 20% fantavoto). Il Modificatore di Difesa
    // (portiere + 3 difensori) continua a usare il voto puro al 100%, non e influenzato da questo peso.
    private double pureVoteWeight;
    private Map<String, List<Integer>> formations;

    @Data
    public static class Dao {
        private String playersTableName;
        private String playerMatchStatsTableName;
        private String fixturesTableName;
        private String availabilityReportsTableName;
        private String rosterTableName;
    }

    @PostConstruct
    public void init() {
        log.info("FantaWinConfigs={}", this);
    }
}