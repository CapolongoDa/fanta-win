package it.capoldan.fantawin.config.msclient;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.ApiClient;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.api.SportMonksApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
public class SportMonksApiConfigurator {
    @Bean
    @Primary
    public SportMonksApi sportMonksApi(@Qualifier("withTracing") RestTemplate restTemplate, FantaWinConfigs cfg) {
        ApiClient newApiClient = new ApiClient(restTemplate);
        newApiClient.setBasePath(cfg.getSportmonksBaseUrl());

        if (StringUtils.hasText(cfg.getSportmonksApiKey())) {
            // SportMonks API 3.0 autentica con il token come query param api_token (v. securitySchemes
            // ApiKeyAuth nello spec sportmonks-api-external.yaml), non con header/Bearer.
            newApiClient.setApiKey(cfg.getSportmonksApiKey());
        } else {
            log.warn("fantawin.sportmonks-api-key non impostata (env SPORTMONKS_API_KEY): " +
                    "le chiamate a SportMonks falliranno con 401/403");
        }

        return new SportMonksApi(newApiClient);
    }
}
