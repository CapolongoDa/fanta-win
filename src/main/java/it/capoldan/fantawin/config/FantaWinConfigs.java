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
    private double defaultMatchDifficulty;
    private double bigPlayerThreshold;
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