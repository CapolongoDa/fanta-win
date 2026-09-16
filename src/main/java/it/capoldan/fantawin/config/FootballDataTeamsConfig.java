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
 * Override manuali opzionali nome squadra reale (come salvato in PlayerEntity.realTeam /
 * FixtureEntity.realTeam) -> teamId numerico di Football-Data.org.
 *
 * FootballDataSyncJob non dipende piu' da questa lista per sapere quali squadre sincronizzare
 * (le deriva dall'anagrafica giocatori): i teamId vengono risolti automaticamente per nome via
 * FootballDataTeamIdResolver (GET /competitions/SA/teams). Una entry qui serve solo come
 * override per una squadra il cui nome in anagrafica non corrisponde a nessun nome/shortName/tla
 * restituito da Football-Data.org.
 *
 * NON valorizzare un id a memoria/indovinato: un ID sbagliato fa leggere i dati di un'altra
 * squadra senza generare alcun errore visibile. Verifica sempre il valore chiamando
 * GET /competitions/SA/teams con il proprio token prima di incollarlo qui.
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