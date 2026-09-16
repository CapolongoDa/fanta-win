package it.capoldan.fantawin.exception;

import org.springframework.http.HttpStatus;

/**
 * Eccezione per quando la rosa non contiene dati sufficienti per completare il calcolo
 * richiesto (es. nessun portiere disponibile, nessun modulo componibile con i giocatori
 * schierabili). Tradotta in un 422 Unprocessable Entity: la richiesta e' sintatticamente
 * valida, ma lo stato della rosa non permette di soddisfarla.
 */
public class IncompleteRosterException extends RuntimeException {

    public IncompleteRosterException(String message) {
        this(message, ExceptionsCodes.ERROR_CODE_INCOMPLETE_ROSTER, null);
    }

    public IncompleteRosterException(String message, String errorCode, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(), message,
                HttpStatus.UNPROCESSABLE_ENTITY.value(), errorCode, null, null, cause);
    }
}
