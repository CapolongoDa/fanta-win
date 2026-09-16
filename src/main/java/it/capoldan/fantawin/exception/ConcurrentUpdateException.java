package it.capoldan.fantawin.exception;

import org.springframework.http.HttpStatus;

/**
 * Eccezione per conflitti di scrittura concorrente su DynamoDB: il controllo di versione
 * ottimistico (@DynamoDbVersionAttribute, es. su PlayerEntity) rifiuta la scrittura perche'
 * un'altra richiesta ha modificato la stessa risorsa nel frattempo. Tradotta in un 409
 * Conflict, cosi' il chiamante sa che puo' ritentare rileggendo lo stato aggiornato -
 * a differenza di IdConflictException, che segnala invece un conflitto sui dati applicativi
 * (es. slot di ruolo gia' pieno in rosa).
 */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(String message, Throwable cause) {
        this(message, ExceptionsCodes.ERROR_CODE_CONCURRENT_UPDATE, cause);
    }

    public ConcurrentUpdateException(String message, String errorCode, Throwable cause) {
        super(HttpStatus.CONFLICT.getReasonPhrase(), message,
                HttpStatus.CONFLICT.value(), errorCode, null, null, cause);
    }
}
