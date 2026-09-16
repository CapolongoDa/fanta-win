package it.capoldan.fantawin.config.msclient;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.ApiClient;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.api.FootballDataApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class FootballDataApiConfigurator {
    @Bean
    @Primary
    public FootballDataApi footballDataApi(@Qualifier("withTracing") RestTemplate restTemplate, FantaWinConfigs cfg){
        ApiClient newApiClient = new ApiClient(restTemplate);
        newApiClient.setBasePath(cfg.getFootballDataBaseUrl());
        return new FootballDataApi( newApiClient );
    }
}
