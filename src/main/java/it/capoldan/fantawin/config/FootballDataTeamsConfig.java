package it.capoldan.fantawin.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * Mappatura nome squadra reale (come salvato in PlayerEntity.realTeam / FixtureEntity.realTeam)
 * -> teamId numerico di Football-Data.org.
 *
 * I valori vanno popolati verificando GET /competitions/SA/teams con il proprio token:
 * NON usare ID a memoria/indovinati, un ID sbagliato fa leggere i dati di un'altra squadra
 * senza generare alcun errore visibile.
 */
@Configuration
@ConfigurationProperties(prefix = "football-data")
@Validated
@Data
@Import({SharedAutoConfiguration.class})
@Slf4j
public class FootballDataTeamsConfig {
    private Map<String, Integer> teamIds = new HashMap<>();
}