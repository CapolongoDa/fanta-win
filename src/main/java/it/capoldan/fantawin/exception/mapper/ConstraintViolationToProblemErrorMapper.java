package it.capoldan.fantawin.exception.mapper;

import it.capoldan.fantawin.exception.ExceptionsCodes;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.ProblemError;
import jakarta.validation.ConstraintViolation;


public class ConstraintViolationToProblemErrorMapper {

    private ConstraintViolationToProblemErrorMapper(){}

    public static ProblemError toProblemError(ConstraintViolation<?> constraintViolation)
    {
        // Codice fisso invece del raw toString() dell'annotazione (es. "@jakarta.validation.constraints.NotNull(...)"):
        // coerente con FieldErrorToProblemErrorMapper, che usa lo stesso codice per lo stesso tipo di errore
        // (parametro/campo mancante o non valido), invece di esporre al chiamante un dettaglio implementativo.
        return ProblemError.builder()
                .code(ExceptionsCodes.ERROR_CODE_GENERIC_INVALIDPARAMETER)
                .detail(constraintViolation.getMessage())
                .element(constraintViolation.getPropertyPath()==null?null:constraintViolation.getPropertyPath().toString())
                .build();
    }

}
