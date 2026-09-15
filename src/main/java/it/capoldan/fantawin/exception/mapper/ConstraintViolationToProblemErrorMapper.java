package it.capoldan.fantawin.exception.mapper;

import it.capoldan.fantawin.generated.openapi.server.v1.dto.ProblemError;

import javax.validation.ConstraintViolation;

public class ConstraintViolationToProblemErrorMapper {

    private ConstraintViolationToProblemErrorMapper(){}

    public static ProblemError toProblemError(ConstraintViolation<?> constraintViolation)
    {
        return ProblemError.builder()
                .code(constraintViolation.getConstraintDescriptor() == null?null: String.valueOf(constraintViolation.getConstraintDescriptor().getAnnotation()))
                .detail(constraintViolation.getMessage())
                .element(constraintViolation.getPropertyPath()==null?null:constraintViolation.getPropertyPath().toString())
                .build();
    }

}
