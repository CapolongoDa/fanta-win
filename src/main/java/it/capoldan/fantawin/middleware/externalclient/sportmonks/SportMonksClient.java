package it.capoldan.fantawin.middleware.externalclient.sportmonks;

import it.capoldan.fantawin.exception.ExternalServiceException;
import it.capoldan.fantawin.exception.HttpResponseException;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.api.SportMonksApi;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.model.FixturesResponse;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.model.TeamSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Component
public class SportMonksClient {

    private static final String LINEUPS_INCLUDE = "lineups";

    private final SportMonksApi delegate;

    public SportMonksClient(SportMonksApi sportMonksApi) {
        this.delegate = sportMonksApi;
    }

    public Mono<TeamSearchResponse> searchTeams(String query) {
        return callWithRetry(() -> delegate.searchTeams(query), "searchTeams query=" + query);
    }

    /** Fixture di una squadra in [startDate, endDate] (yyyy-MM-dd), con formazione ufficiale se gia' pubblicata. */
    public Mono<FixturesResponse> getFixturesBetweenForTeam(Integer teamId, String startDate, String endDate) {
        return callWithRetry(() -> delegate.getFixturesBetweenForTeam(startDate, endDate, teamId, LINEUPS_INCLUDE),
                "getFixturesBetweenForTeam teamId=" + teamId + " [" + startDate + "," + endDate + "]");
    }

    private <T> Mono<T> callWithRetry(Supplier<T> call, String callDescription) {
        return Mono.fromCallable(call::get)
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(SportMonksClient::isRetryable)
                        .onRetryExhaustedThrow((spec, signal) ->
                                new ExternalServiceException(
                                        "SportMonks non raggiungibile dopo retry per " + callDescription,
                                        signal.failure())))
                .doOnError(ex -> log.error("Errore chiamando SportMonks ({})", callDescription, ex));
    }

    private static boolean isRetryable(Throwable ex) {
        // Stesso motivo del FootballDataClient: RestTemplateResponseErrorHandler rilancia sempre
        // HttpResponseException per 4xx/5xx, non le classiche eccezioni Spring.
        if (ex instanceof HttpResponseException hre) {
            int status = hre.getStatusCode();
            return status == 429 || status >= 500;
        }
        return ex instanceof ResourceAccessException;
    }
}
