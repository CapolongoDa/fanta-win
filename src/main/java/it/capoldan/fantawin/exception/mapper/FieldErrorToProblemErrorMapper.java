package it.capoldan.fantawin.exception.mapper;


import it.capoldan.fantawin.generated.openapi.server.v1.dto.ProblemError;
import org.springframework.validation.FieldError;

import static it.capoldan.fantawin.exception.ExceptionsCodes.ERROR_CODE_GENERIC_INVALIDPARAMETER;

public class FieldErrorToProblemErrorMapper {

    private FieldErrorToProblemErrorMapper(){}

    public static ProblemError toProblemError(FieldError fieldError)
    {
        return  ProblemError.builder()
                .code(ERROR_CODE_GENERIC_INVALIDPARAMETER)
                .detail(fieldError.getDefaultMessage())
                .element(fieldError.getField())
                .build();
    }

}
