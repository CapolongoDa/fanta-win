package it.capoldan.fantawin.config.msclient;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.ApiClient;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.api.FootballDataApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
public class FootballDataApiConfigurator {
    @Bean
    @Primary
    public FootballDataApi footballDataApi(@Qualifier("withTracing") RestTemplate restTemplate, FantaWinConfigs cfg){
        ApiClient newApiClient = new ApiClient(restTemplate);
        newApiClient.setBasePath(cfg.getFootballDataBaseUrl());

        if (StringUtils.hasText(cfg.getFootballDataApiKey())) {
            // Football-Data.org v4 autentica con header X-Auth-Token (vedi securitySchemes
            // ApiKeyAuth nello spec football-data-api-external.yaml), non con Bearer/OAuth.
            newApiClient.setApiKey(cfg.getFootballDataApiKey());
        } else {
            log.warn("fantawin.football-data-api-key non impostata (env FOOTBALL_DATA_API_KEY): " +
                    "le chiamate a Football-Data.org falliranno con 403");
        }

        return new FootballDataApi( newApiClient );
    }
}
