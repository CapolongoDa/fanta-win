package it.capoldan.fantawin.exception;

import org.springframework.http.HttpStatus;

/**
 * Eccezione per quando una dipendenza esterna non e' raggiungibile a livello di trasporto
 * (es. DynamoDB/LocalStack giu', connessione rifiutata, timeout). Tradotta in un 503 Service
 * Unavailable: segnala al chiamante che puo' ritentare piu' tardi, a differenza di un 500
 * generico che implicherebbe un bug nel nostro codice.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message, Throwable cause) {
        this(message, ExceptionsCodes.ERROR_CODE_SERVICE_UNAVAILABLE, cause);
    }

    public ServiceUnavailableException(String message, String errorCode, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(), message,
                HttpStatus.SERVICE_UNAVAILABLE.value(), errorCode, null, null, cause);
    }
}
