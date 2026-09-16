package it.capoldan.fantawin.exception;

import org.springframework.http.HttpStatus;

/**
 * Eccezione per errori nella comunicazione con servizi esterni (es. Football-Data.org),
 * dopo che i retry configurati sono stati esauriti. Tradotta in un 502 Bad Gateway: il
 * problema e' a monte (il servizio esterno non risponde o risponde con errore), non un
 * bug nel nostro codice - distinzione importante per chi guarda i log/allarmi in produzione.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message, Throwable cause) {
        this(message, ExceptionsCodes.ERROR_CODE_EXTERNAL_SERVICE_ERROR, cause);
    }

    public ExternalServiceException(String message, String errorCode, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY.getReasonPhrase(), message,
                HttpStatus.BAD_GATEWAY.value(), errorCode, null, null, cause);
    }
}
