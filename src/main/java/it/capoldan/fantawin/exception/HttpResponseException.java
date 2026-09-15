package it.capoldan.fantawin.exception;

import it.capoldan.fantawin.generated.openapi.server.v1.dto.ProblemError;
import lombok.Getter;

import java.util.List;

import static it.capoldan.fantawin.exception.ExceptionsCodes.ERROR_CODE_HTTPRESPONSE_GENERIC_ERROR;


@Getter
public class HttpResponseException extends RuntimeException {
    private final int statusCode;

    public HttpResponseException(String message, int statusCode) {
        super(message, message, statusCode, ERROR_CODE_HTTPRESPONSE_GENERIC_ERROR, null, null);
        this.statusCode = statusCode;
    }

    public HttpResponseException(String message, String description, int statusCode, List<ProblemError> problems, Throwable cause) {
        super(message, description, statusCode, problems, cause );
        this.statusCode = statusCode;
    }
}
