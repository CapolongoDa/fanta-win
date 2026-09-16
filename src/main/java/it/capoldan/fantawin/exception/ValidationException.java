package it.capoldan.fantawin.exception;

import it.capoldan.fantawin.generated.openapi.server.v1.dto.ProblemError;
import org.springframework.http.HttpStatus;
import java.util.List;


/**
 * Eccezione di validazione di base, viene tradotta con un errore 400
 * Pensata per tradurre facilmente la validation exception e per generare
 * i problem relativi ai problemi di validazione.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message, List<ProblemError> problemErrorList, Throwable cause) {
        super( HttpStatus.BAD_REQUEST.getReasonPhrase(), message, HttpStatus.BAD_REQUEST.value(), problemErrorList, cause  );
    }

}
