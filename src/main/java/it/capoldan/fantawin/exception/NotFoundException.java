package it.capoldan.fantawin.exception;

import org.springframework.http.HttpStatus;

/** Eccezione tradotta in un 404 Not Found. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message, String errorCode) {
        this(message, errorCode, null);
    }

    public NotFoundException(String message, String errorCode, Throwable cause) {
        super(HttpStatus.NOT_FOUND.getReasonPhrase(), message,
                HttpStatus.NOT_FOUND.value(), errorCode, null, null, cause);
    }
}