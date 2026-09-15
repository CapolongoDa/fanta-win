package it.capoldan.fantawin.exception;


import it.capoldan.fantawin.exception.config.Exception;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.Problem;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.ProblemError;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static it.capoldan.fantawin.exception.ExceptionsCodes.ERROR_CODE_GENERIC_ERROR;


/**
 * Eccezione base da estendere all'occorrenza, genera già in automatico il problem da ritornare 
 */
@Getter
@Slf4j
public class RuntimeException extends NestedRuntimeException implements Exception {

    private Problem problem;

    public RuntimeException(@NotNull String message, @NotNull  String description, int status, @NotNull  String errorcode, String element, String detail) {
        this(message, description, status, errorcode, element, detail, null);
    }

    public RuntimeException(@NotNull String message, @NotNull  String description, int status, @NotNull  String errorcode, String element, String detail, Throwable cause) {
        this(message, description, status, List.of(ProblemError.builder()
                .code(errorcode)
                .detail(detail)
                .element(element)
                .build()), cause);
    }

    public RuntimeException(@NotNull String message, @NotNull  String description, int status, @NotNull  List<ProblemError> problemErrorList) {
        this(message, description, status, problemErrorList, null);
    }

    public RuntimeException(@NotNull String message, @NotNull String description, int status, @NotNull List<ProblemError> problemErrorList, Throwable cause) {
        super(message, cause);
        problem = new Problem();

        if (!StringUtils.hasText(message))
            message = HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase();
        if (!StringUtils.hasText(description))
            description = HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase();
        if (CollectionUtils.isEmpty(problemErrorList))
            problemErrorList = new ArrayList<>();

        problem.setType("GENERIC_ERROR");
        problem.setTitle(message.substring(0, Math.min(message.length(), 64)));
        problem.setDetail(description.substring(0, Math.min(description.length(), 4096)));
        problem.setStatus(status<100?100:(Math.min(status, 600)));
        problem.setTimestamp(Instant.now().atOffset(ZoneOffset.UTC));

        // non deve mai essere vuoto, quindi se per qualche motivo lo è, aggiungo un errore generico
        if (problemErrorList.isEmpty())
        {
            problemErrorList.add(ProblemError.builder()
                            .code(ERROR_CODE_GENERIC_ERROR)
                            .detail("none")
                    .build());
        }
        problem.setErrors(problemErrorList.stream().map(problemError -> {
                    if (problemError.getDetail()!=null)
                        problemError.setDetail(problemError.getDetail().substring(0, Math.min(problemError.getDetail().length(), 1024)));
                    else
                        problemError.setDetail("none");

                    // mappo nel probleerror generato dallo YAML
                    return ProblemError.builder()
                            .code(problemError.getCode())
                            .detail(problemError.getDetail())
                            .element(problemError.getElement())
                            .build();
                }).toList());

    }

    public RuntimeException(Problem problem) {
        this(problem.getTitle(), problem.getDetail(), problem.getStatus(), problem.getErrors(), null);
        this.problem = problem;
    }

}
