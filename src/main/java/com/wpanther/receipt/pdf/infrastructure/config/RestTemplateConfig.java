package com.wpanther.receipt.pdf.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate with timeout settings.
 * <p>
 * The RestTemplate is configured with connect and read timeouts to prevent
 * thread exhaustion when the signed XML service is unresponsive.
 * <p>
 * Circuit breakers are configured via application.yml under resilience4j.circuitbreaker:
 * - signedXmlFetch: for signed XML fetch operations
 * - minio: for MinIO S3 operations
 */
@Configuration
@Slf4j
public class RestTemplateConfig {

    @Value("${app.rest-client.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${app.rest-client.read-timeout:30000}")
    private int readTimeout;

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public RestTemplateConfig(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    /**
     * Set up circuit breaker event logging after Spring context initialization.
     */
    @PostConstruct
    public void setupCircuitBreakerLogging() {
        CircuitBreaker signedXmlFetch = circuitBreakerRegistry.circuitBreaker("signedXmlFetch");
        CircuitBreaker minio = circuitBreakerRegistry.circuitBreaker("minio");

        signedXmlFetch.getEventPublisher()
                .onStateTransition(event -> log.info("Circuit breaker 'signedXmlFetch' state transition: {}",
                        event.getStateTransition()));

        minio.getEventPublisher()
                .onStateTransition(event -> log.info("Circuit breaker 'minio' state transition: {}",
                        event.getStateTransition()));

        log.info("Circuit breaker event logging configured for 'signedXmlFetch' and 'minio'");
    }

    /**
     * RestTemplate with configured timeouts for fetching signed XML documents.
     * <p>
     * Timeouts are configurable via:
     * - app.rest-client.connect-timeout (default: 5000ms)
     * - app.rest-client.read-timeout (default: 30000ms)
     */
    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        log.info("Configured RestTemplate with connectTimeout={}ms, readTimeout={}ms",
                connectTimeout, readTimeout);

        return new RestTemplate(factory);
    }
}
