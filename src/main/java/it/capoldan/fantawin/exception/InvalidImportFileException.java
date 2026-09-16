package it.capoldan.fantawin.exception;

import org.springframework.http.HttpStatus;

/** Eccezione tradotta in un 400 Bad Request: file di import assente, vuoto o non leggibile come CSV. */
public class InvalidImportFileException extends RuntimeException {

    public InvalidImportFileException(String message) {
        this(message, null);
    }

    public InvalidImportFileException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST.getReasonPhrase(), message,
                HttpStatus.BAD_REQUEST.value(), ExceptionsCodes.ERROR_CODE_INVALID_IMPORT_FILE, null, null, cause);
    }
}
