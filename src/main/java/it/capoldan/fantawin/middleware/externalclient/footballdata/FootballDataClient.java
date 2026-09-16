package it.capoldan.fantawin.middleware.externalclient.footballdata;

import it.capoldan.fantawin.exception.ExternalServiceException;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.api.FootballDataApi;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.model.MatchesResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Component
public class FootballDataClient {

    private final FootballDataApi delegate;

    public FootballDataClient(FootballDataApi footballDataApiClient) {
        this.delegate = footballDataApiClient;
    }

    public Mono<MatchesResponse> getTeamMatches(int teamId, String status, int limit) {
        return Mono.fromCallable(() -> delegate.getTeamMatches(teamId, status, limit))
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(FootballDataClient::isRetryable)
                        .onRetryExhaustedThrow((spec, signal) ->
                                new ExternalServiceException(
                                        "Football-Data.org non raggiungibile dopo retry per teamId=" + teamId,
                                        signal.failure())))
                .doOnError(ex -> log.error("Errore chiamando Football-Data.org, teamId={}", teamId, ex));
    }

    private static boolean isRetryable(Throwable ex) {
        if (ex instanceof RestClientResponseException rcre) {
            int status = rcre.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        // errori di rete/timeout: RestTemplate li incapsula in ResourceAccessException (causa IOException)
        return ex instanceof ResourceAccessException;
    }
}