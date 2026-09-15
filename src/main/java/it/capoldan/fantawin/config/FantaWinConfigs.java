package it.capoldan.fantawin.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties( prefix = "fantawin")
@Validated
@Data
@Import({SharedAutoConfiguration.class})
@Slf4j
public class FantaWinConfigs {
    private Dao dao;

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