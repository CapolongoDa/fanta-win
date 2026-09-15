package it.capoldan.fantawin.exception;

import org.springframework.http.HttpStatus;

public class TooManyRequestException extends RuntimeException {

    public TooManyRequestException(String message, Throwable cause) {
        this(message, ExceptionsCodes.ERROR_CODE_TOO_MANY_REQUESTS, cause);
    }

    public TooManyRequestException(String message, String errorCode) {
        this(message, errorCode, null);
    }

    public TooManyRequestException(String message, String errorCode, Throwable cause) {
        super(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), message, HttpStatus.TOO_MANY_REQUESTS.value(), errorCode, null, null, cause);
    }

}
