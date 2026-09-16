package it.capoldan.fantawin.config.springbootcfg;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class RestTemplateFactory {

    @Bean(name = "withTracing")
    public RestTemplate restTemplateWithTracing(
            @Value("${fantawin.rest-template.retry-max-attempts:3}") int retryMaxAttempts,
            @Value("${fantawin.rest-template.connection-timeout:5000}") int connectionTimeout,
            @Value("${fantawin.rest-template.read-timeout:10000}") int readTimeout) {
        //RetryTemplate nel parametro retryMaxAttempts vuole le invocazioni totali (compresa la prima che non è fallita)
        RestTemplate template = new RestTemplateRetryable(retryMaxAttempts + 1);
        configureRestTemplate(connectionTimeout, readTimeout, template);
        return template;
    }

    protected void configureRestTemplate(int connectionTimeout, int readTimeout, RestTemplate template) {
        SimpleClientHttpRequestFactory clientHttpRequestFactory = new SimpleClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(connectionTimeout);
        clientHttpRequestFactory.setReadTimeout(readTimeout);
        template.setRequestFactory(clientHttpRequestFactory);
        enrichWithTracing(template);
        template.setErrorHandler(new RestTemplateResponseErrorHandler());
    }

    public void enrichWithTracing(RestTemplate template) {
        List<ClientHttpRequestInterceptor> interceptors = template.getInterceptors();
        if (CollectionUtils.isEmpty(interceptors)) {
            interceptors = new ArrayList<>();
        }
        interceptors.add(new RestTemplateHeaderModifierInterceptor());
        template.setInterceptors(interceptors);
    }

    public void enrichURIEncoding(RestTemplate template) {
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);
        template.setUriTemplateHandler(uriBuilderFactory);
    }

    // interceptor per il trace verso altri MS
    @Value("${pn.log.trace-id-header:x-trace-id}")
    private String traceIdHeader;

    public class RestTemplateHeaderModifierInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            String traceId = MDC.get("trace_id");
            if (traceId != null) {
                request.getHeaders().add(traceIdHeader, traceId);
            }
            return execution.execute(request, body);
        }
    }
}