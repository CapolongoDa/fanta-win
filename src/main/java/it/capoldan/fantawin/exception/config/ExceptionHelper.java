package it.capoldan.fantawin.exception.config;


import it.capoldan.fantawin.exception.InternalException;
import it.capoldan.fantawin.exception.RuntimeException;
import it.capoldan.fantawin.exception.TooManyRequestException;
import it.capoldan.fantawin.exception.mapper.ConstraintViolationToProblemErrorMapper;
import it.capoldan.fantawin.exception.mapper.FieldErrorToProblemErrorMapper;
import it.capoldan.fantawin.exception.mapper.ValidationExceptionBuilder;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.Problem;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.ProblemError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

import static it.capoldan.fantawin.exception.ExceptionsCodes.ERROR_CODE_GENERIC_ERROR;
import static it.capoldan.fantawin.exception.ExceptionsCodes.ERROR_CODE_WEB_GENERIC_ERROR;

@Slf4j
@Component
public class ExceptionHelper {

    public static final String MESSAGE_SEE_LOGS_FOR_DETAILS = "See logs for details in ";
    public static final String MESSAGE_UNEXPECTED_ERROR = "Unexpected error";
    public static final String MESSAGE_HANDLED_ERROR = "Handled error";
    private final Map<String, String> validationMap = new HashMap<>();

    @Value("${spring.application.name:}")
    private String applicationName;

    public ExceptionHelper(Optional<IValidationCustomMapper> customValidationMapper){

        initValidationMap();

        customValidationMapper.ifPresent(iValidationCustomMapper -> validationMap.putAll(iValidationCustomMapper.getValidationCodeCustomMapping()));

    }



    public Problem handleException(Throwable ex){
        // gestione exception e generazione fault
        Problem res;

        // gestione dedicata delle constraintviolation, lanciate da spring direttamente
        if (ex instanceof ConstraintViolationException constraintViolationException) {
            // eccezione di constraint, recupero le info dei campi
            ex = new ValidationExceptionBuilder<>(this)
                    .validationErrors(constraintViolationException.getConstraintViolations())
                    .cause(ex)
                    .message(ex.getMessage())
                    .build();
        }
        else if (ex instanceof WebClientResponseException webClientResponseException) {
            // per il caso di 429, si vuole ritornare il 429
            if (webClientResponseException.getStatusCode().equals(HttpStatus.TOO_MANY_REQUESTS))
                ex = new TooManyRequestException(ex.getMessage(), ex);
        }
        else if (ex instanceof org.springframework.web.bind.support.WebExchangeBindException webExchangeBindException){
            // eccezione di spring riguardante errori di validazione, recupero le info dei campi
            ex = new ValidationExceptionBuilder<>(this)
                    .fieldErrors(webExchangeBindException.getFieldErrors())
                    .cause(ex)
                    .message(ex.getMessage())
                    .build();
        }
        else if (ex instanceof ResponseStatusException responseStatusException){
            // eccezione di spring riguardante errori di altra natura
            ex = new RuntimeException(Objects.requireNonNull(responseStatusException.getMessage() == null ? "Web error" : responseStatusException.getMessage()),
                    Objects.requireNonNull(responseStatusException.getReason() == null ?  "Web error" : responseStatusException.getReason()),
                    responseStatusException.getStatusCode().value(),
                    ERROR_CODE_WEB_GENERIC_ERROR, null, null, responseStatusException);
        }

        // se non è una nostra Exception, la incapsulo in un errore interno generico
        if (!(ex instanceof Exception)) {
            ex = new InternalException("Errore generico", ERROR_CODE_GENERIC_ERROR, ex);
        }

        // popolo il problem a partire dall'exception risolta
        res = ((Exception) ex).getProblem();

        if (res.getStatus() >= 500)
            log.error("exception " + res.getStatus() + " catched problem={}", res, ex);
        else
            log.warn("exception " + res.getStatus() + " catched problem={}", res, ex);

        return offuscateProblem(res);
    }

    private Problem offuscateProblem(Problem res){
        if (res.getStatus() >= 500)
        {
            res.setTitle(MESSAGE_UNEXPECTED_ERROR);
        }
        else
        {
            res.setTitle(MESSAGE_HANDLED_ERROR);
        }

        res.setDetail(MESSAGE_SEE_LOGS_FOR_DETAILS + getCurrentApplicationName());

        return res;
    }

    private String getCurrentApplicationName(){
        return applicationName;
    }

    public String generateFallbackProblem(){
        String fallback = """
                {
                    "status": 500,
                    "title": "Internal Server Error",
                    "detail": "Cannot output problem",
                    "traceId": "{traceid}",
                    "timestamp": "{timestamp}",
                    "errors": [
                        {
                            "code": "{errorcode}",
                            "element": null,
                            "detail": null
                        }
                    ]
                }
                """;

        fallback = fallback.replace("{traceid}", UUID.randomUUID().toString());
        fallback = fallback.replace("{timestamp}", Instant.now().toString());
        fallback = fallback.replace("{errorcode}", ERROR_CODE_GENERIC_ERROR);

        return fallback;
    }

    public List<ProblemError> generateProblemErrorsFromConstraintViolation(Set<? extends ConstraintViolation<?>> constraintViolations)
    {
        return constraintViolations.stream().map(ConstraintViolationToProblemErrorMapper::toProblemError).toList();
    }


    public List<ProblemError> generateProblemErrorsFromFieldError(List<FieldError> fieldErrors)
    {
        return fieldErrors.stream().map(FieldErrorToProblemErrorMapper::toProblemError).toList();
    }


    private void initValidationMap() {
        //da aggiunger
    }
}
