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
 * Override manuali opzionali nome squadra reale (come salvato in PlayerEntity.realTeam) -> teamId
 * numerico di SportMonks.
 *
 * SportMonksTeamIdResolver risolve i teamId automaticamente per nome via GET /teams/search/{query},
 * prendendo il primo risultato restituito da SportMonks (il piu' pertinente secondo la loro ricerca).
 * Una entry qui serve come override per una squadra il cui nome in anagrafica non produce come primo
 * risultato la squadra giusta (es. ricerca ambigua con squadre estere omonime).
 *
 * NON valorizzare un id a memoria/indovinato: un ID sbagliato fa leggere i dati di un'altra squadra
 * senza generare alcun errore visibile. Verifica sempre il valore chiamando
 * GET /teams/search/{nome} con il proprio token prima di incollarlo qui.
 */
@Configuration
@ConfigurationProperties(prefix = "sportmonks")
@Validated
@Data
@Import({SharedAutoConfiguration.class})
@Slf4j
public class SportMonksTeamsConfig {
    private Map<String, Integer> teamIds = new HashMap<>();
}
