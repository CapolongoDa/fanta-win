package it.capoldan.fantawin.middleware.externalclient.footballdata;

import it.capoldan.fantawin.exception.ExternalServiceException;
import it.capoldan.fantawin.exception.HttpResponseException;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.api.FootballDataApi;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.model.MatchesResponse;
import it.capoldan.fantawin.generated.openapi.msclient.football_data.model.TeamsResponse;
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
public class FootballDataClient {

    private final FootballDataApi delegate;

    public FootballDataClient(FootballDataApi footballDataApiClient) {
        this.delegate = footballDataApiClient;
    }

    public Mono<MatchesResponse> getTeamMatches(int teamId, String status, int limit) {
        return callWithRetry(() -> delegate.getTeamMatches(teamId, status, limit),
                "getTeamMatches teamId=" + teamId);
    }

    /** Elenco squadre di una competizione (es. "SA" per la Serie A), usato per risolvere i teamId per nome. */
    public Mono<TeamsResponse> getCompetitionTeams(String competitionCode) {
        return callWithRetry(() -> delegate.getCompetitionTeams(competitionCode),
                "getCompetitionTeams code=" + competitionCode);
    }

    private <T> Mono<T> callWithRetry(Supplier<T> call, String callDescription) {
        return Mono.fromCallable(call::get)
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(FootballDataClient::isRetryable)
                        .onRetryExhaustedThrow((spec, signal) ->
                                new ExternalServiceException(
                                        "Football-Data.org non raggiungibile dopo retry per " + callDescription,
                                        signal.failure())))
                .doOnError(ex -> log.error("Errore chiamando Football-Data.org ({})", callDescription, ex));
    }

    private static boolean isRetryable(Throwable ex) {
        // Il RestTemplate "withTracing" ha un ResponseErrorHandler custom (RestTemplateResponseErrorHandler)
        // che intercetta ogni 4xx/5xx e rilancia SEMPRE HttpResponseException, non le classiche
        // HttpClientErrorException/HttpServerErrorException di Spring (RestClientResponseException):
        // controllare quel tipo qui non avrebbe mai intercettato nulla e i retry su 429/5xx non sarebbero
        // mai scattati.
        if (ex instanceof HttpResponseException hre) {
            int status = hre.getStatusCode();
            return status == 429 || status >= 500;
        }
        // errori di rete/timeout: RestTemplate li incapsula in ResourceAccessException (causa IOException)
        return ex instanceof ResourceAccessException;
    }
}