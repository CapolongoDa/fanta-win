package it.capoldan.fantawin.exception;

import org.springframework.http.HttpStatus;

/**
 * Eccezione per incoerenze rilevate tra le tabelle DynamoDB (es. un playerId referenziato
 * dalla rosa ma assente dall'anagrafica giocatori). Resta un 500: e' un problema dei nostri
 * dati, non una richiesta errata del chiamante - ma va distinto da un generico errore interno
 * per poterlo individuare/allarmare separatamente nei log e nelle metriche.
 */
public class DataIntegrityException extends RuntimeException {

    public DataIntegrityException(String message) {
        this(message, ExceptionsCodes.ERROR_CODE_DATA_INTEGRITY, null);
    }

    public DataIntegrityException(String message, Throwable cause) {
        this(message, ExceptionsCodes.ERROR_CODE_DATA_INTEGRITY, cause);
    }

    public DataIntegrityException(String message, String errorCode, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), message,
                HttpStatus.INTERNAL_SERVER_ERROR.value(), errorCode, null, null, cause);
    }
}
