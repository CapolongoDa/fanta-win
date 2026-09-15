package it.capoldan.fantawin.config.springbootcfg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.capoldan.fantawin.exception.HttpResponseException;
import it.capoldan.fantawin.exception.mapper.DtoProblemToProblemErrorMapper;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.Problem;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResponseErrorHandler;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Slf4j
public class RestTemplateResponseErrorHandler
        implements ResponseErrorHandler {

    private final ObjectMapper objectMapper;

    public RestTemplateResponseErrorHandler() {
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public boolean hasError(ClientHttpResponse httpResponse)
            throws IOException {
        return httpResponse.getStatusCode().is4xxClientError()
                || httpResponse.getStatusCode().is5xxServerError();
    }

    @Override
    public void handleError(@NotNull ClientHttpResponse response) throws IOException {
        String body = null;

        try {
            body = getBody(response);
        }
        catch (IOException e) {
            log.trace("Empty body");
        }
        
        String errorMsg = String.format(
                "Error with statusCode=%s and body=%s",
                response.getStatusCode(),
                body
        );
        
        log.error(errorMsg);

        proceedWithThrowHttpResponseException(body, response.getRawStatusCode(), errorMsg);
    }

    @Override
    public void handleError(@NotNull URI url, @NotNull HttpMethod method, @NotNull ClientHttpResponse response)
            throws IOException {

        String body = null;
        try {
            body = getBody(response);
        }
        catch (IOException e) {
            log.trace("Empty body");
        }

        String errorMsg = String.format(
                "Error in call url=%s method=%s statusCode=%s and body=%s",
                url, 
                method,
                response.getStatusCode(),
                body
        );
        
        log.error(errorMsg);

        proceedWithThrowHttpResponseException(body, response.getRawStatusCode(), errorMsg);
    }

    private void proceedWithThrowHttpResponseException(String body, int rawStatusCode, String errorMsg){
        Problem problem = null;
        try {
            problem = objectMapper.readValue(body, Problem.class);
        } catch (Exception e) {
            log.info("cannot parse body as problem", e);
        }

        if (problem != null && problem.getErrors() != null)
            throw new HttpResponseException(problem.getTitle(), problem.getDetail(), rawStatusCode, problem.getErrors().stream().map(DtoProblemToProblemErrorMapper::toProblemError).toList(), null);
        else
            throw new HttpResponseException(errorMsg, rawStatusCode);
    }

    @NotNull
    private String getBody(ClientHttpResponse response) throws IOException {
        String body;
        try (InputStream responseBody = response.getBody()) {
            body = StreamUtils.copyToString(responseBody, StandardCharsets.UTF_8);
        }
        return body;
    }
}