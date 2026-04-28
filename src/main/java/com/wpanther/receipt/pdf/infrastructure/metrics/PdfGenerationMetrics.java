package com.wpanther.receipt.pdf.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PdfGenerationMetrics {

    private final Counter generatedCounter;
    private final Counter failedCounter;
    private final Counter retryExhaustedCounter;

    public PdfGenerationMetrics(MeterRegistry registry) {
        this.generatedCounter = Counter.builder("pdf.generation.receipt.generated")
                .description("Number of receipt PDFs successfully generated")
                .register(registry);
        this.failedCounter = Counter.builder("pdf.generation.receipt.failed")
                .description("Number of receipt PDF generation failures")
                .register(registry);
        this.retryExhaustedCounter = Counter.builder("pdf.generation.receipt.retry_exhausted")
                .description("Number of receipt PDF generation attempts that exhausted retries")
                .register(registry);
    }

    public void recordGenerated() { generatedCounter.increment(); }
    public void recordFailed() { failedCounter.increment(); }
    public void recordRetryExhausted(String sagaId, String documentId, String documentNumber) {
        retryExhaustedCounter.increment();
    }
}
