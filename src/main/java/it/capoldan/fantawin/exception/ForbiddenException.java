package it.capoldan.fantawin.exception;

import org.springframework.http.HttpStatus;

/** Eccezione tradotta in un 403 Forbidden: l'utente autenticato non e' il proprietario della risorsa richiesta. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message, String errorCode) {
        this(message, errorCode, null);
    }

    public ForbiddenException(String message, String errorCode, Throwable cause) {
        super(HttpStatus.FORBIDDEN.getReasonPhrase(), message,
                HttpStatus.FORBIDDEN.value(), errorCode, null, null, cause);
    }
}
