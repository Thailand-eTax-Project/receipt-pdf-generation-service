package com.wpanther.receipt.pdf.infrastructure.adapter.out.client;

import com.wpanther.receipt.pdf.application.port.out.SignedXmlFetchPort;
import com.wpanther.receipt.pdf.application.port.out.SignedXmlFetchPort.SignedXmlFetchException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestTemplateSignedXmlFetcher implements SignedXmlFetchPort {

    private final RestTemplate restTemplate;

    @Override
    @CircuitBreaker(name = "signedXmlFetch", fallbackMethod = "fallbackOnFailure")
    public String fetch(String signedXmlUrl) {
        log.debug("Fetching signed XML from {}", signedXmlUrl);
        String response = restTemplate.getForObject(signedXmlUrl, String.class);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException(
                    "Received null or empty signed XML response from: " + signedXmlUrl);
        }
        log.debug("Successfully fetched signed XML, size: {} bytes", response.length());
        return response;
    }

    private String fallbackOnFailure(String signedXmlUrl, Throwable throwable) {
        throw new SignedXmlFetchException(
                "Circuit breaker 'signedXmlFetch' is OPEN — " +
                "document-storage-service is degraded. URL: " + signedXmlUrl, throwable);
    }
}
