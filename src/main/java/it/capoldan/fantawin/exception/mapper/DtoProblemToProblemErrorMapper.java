package it.capoldan.fantawin.exception.mapper;

import it.capoldan.fantawin.generated.openapi.server.v1.dto.ProblemError;

public class DtoProblemToProblemErrorMapper {

    private DtoProblemToProblemErrorMapper(){}

    public static ProblemError toProblemError(ProblemError dtoProblemError)
    {
        return  ProblemError.builder()
                .code(dtoProblemError.getCode())
                .detail(dtoProblemError.getDetail())
                .element(dtoProblemError.getElement())
                .build();
    }

}
