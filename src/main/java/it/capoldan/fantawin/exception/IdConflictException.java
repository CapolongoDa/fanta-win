package it.capoldan.fantawin.exception;

import it.capoldan.fantawin.generated.openapi.server.v1.dto.ProblemError;

import java.util.Map;

public class IdConflictException extends RuntimeException {

    public IdConflictException(Map<String, String> invalidFields) {
        this(ExceptionsCodes.ERROR_CODE_GENERIC_INVALIDPARAMETER_DUPLICATED, invalidFields);
    }


    public IdConflictException(String errorCode, Map<String, String> invalidFields) {
       this(errorCode, invalidFields, null);
    }

    public IdConflictException(String errorCode, Map<String, String> invalidFields, Throwable cause) {
        super("Conflict", "Some resources are in conflict", 409,
                invalidFields.keySet().stream().map(x -> ProblemError.builder()
                        .code(errorCode)
                        .element(x)
                        .detail(x  + "=" + invalidFields.get(x))
                        .build()).toList(), cause );
    }
}
