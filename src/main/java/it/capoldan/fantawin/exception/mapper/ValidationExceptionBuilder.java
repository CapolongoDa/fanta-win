package it.capoldan.fantawin.exception.mapper;

import it.capoldan.fantawin.exception.ValidationException;
import it.capoldan.fantawin.exception.config.ExceptionHelper;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.ProblemError;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.FieldError;
import jakarta.validation.ConstraintViolation;
import java.util.List;
import java.util.Set;

public class ValidationExceptionBuilder<T> {
    private final ExceptionHelper exceptionHelper;

    private Set<ConstraintViolation<? extends Object>> validationErrors;
    private List<FieldError> fieldErrors;
    private List<ProblemError> problemErrorList;
    private Throwable cause;
    private String message;

    public ValidationExceptionBuilder(ExceptionHelper exceptionHelper) {
        this.exceptionHelper = exceptionHelper;
    }

    public ValidationExceptionBuilder<T> validationErrors(Set<ConstraintViolation<? extends Object>> validationErrors) {
        this.validationErrors = validationErrors;
        return this;
    }

    public ValidationExceptionBuilder<T> fieldErrors(List<FieldError> fieldErrors) {
        this.fieldErrors = fieldErrors;
        return this;
    }

    public ValidationExceptionBuilder<T> problemErrorList(List<ProblemError> problemErrorList) {
        this.problemErrorList = problemErrorList;
        return this;
    }

    public ValidationExceptionBuilder<T> cause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    public ValidationExceptionBuilder<T> message(String message) {
        this.message = message;
        return this;
    }

    public ValidationException build() {
        if (!CollectionUtils.isEmpty(validationErrors))
            return new ValidationException(message, exceptionHelper.generateProblemErrorsFromConstraintViolation(this.validationErrors), cause);
        else if (!CollectionUtils.isEmpty(fieldErrors))
            return new ValidationException(message, exceptionHelper.generateProblemErrorsFromFieldError(this.fieldErrors), cause);
        else
            return new ValidationException(message, problemErrorList, cause);
    }
}
