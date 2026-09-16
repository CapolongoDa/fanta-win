package it.capoldan.fantawin.exception.config;

import it.capoldan.fantawin.generated.openapi.server.v1.dto.Problem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

/**
 * Equivalente reattivo di ResponseEntityExceptionHandler (quello esistente e'
 * dichiaratamente per microservizi Spring MVC classici). ExceptionHelper gestisce
 * gia' tipi WebFlux-specifici (WebClientResponseException, WebExchangeBindException),
 * quindi mancava solo il wiring @RestControllerAdvice per intercettare le eccezioni
 * sollevate dai delegate dei controller reattivi di questo microservizio.
 */
@Slf4j
@RestControllerAdvice
public class WebFluxExceptionHandler {

    private final ExceptionHelper exceptionHelper;

    public WebFluxExceptionHandler(ExceptionHelper exceptionHelper) {
        this.exceptionHelper = exceptionHelper;
    }

    @ExceptionHandler(Throwable.class)
    public Mono<ResponseEntity<Problem>> handleException(Throwable ex) {
        Problem problem = exceptionHelper.handleException(ex);
        return Mono.just(ResponseEntity.status(problem.getStatus()).body(problem));
    }
}