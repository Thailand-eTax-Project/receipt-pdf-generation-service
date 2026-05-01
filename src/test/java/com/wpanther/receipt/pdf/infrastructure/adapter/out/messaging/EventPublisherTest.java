package com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.receipt.pdf.application.dto.event.ReceiptPdfGeneratedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for EventPublisher.
 *
 * After layer separation refactor, ReceiptPdfGeneratedEvent lives in
 * application.dto.event package.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublisher Unit Tests")
class EventPublisherTest {

    @Mock
    private OutboxService outboxService;

    private ObjectMapper objectMapper;
    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        eventPublisher = new EventPublisher(outboxService, objectMapper);
    }

    @Test
    @DisplayName("publishGenerated() calls OutboxService with correct parameters")
    void testPublishGenerated() {
        // Given
        String documentId = "doc-123";
        String documentNumber = "RCP-2024-001";
        String documentUrl = "http://localhost:9000/receipts/test.pdf";
        long fileSize = 12345L;
        boolean xmlEmbedded = true;
        String correlationId = "corr-456";

        ReceiptPdfGeneratedEvent event = new ReceiptPdfGeneratedEvent(
                "saga-001", documentId, documentNumber, documentUrl, fileSize, xmlEmbedded, correlationId);

        // When
        eventPublisher.publishGenerated(event);

        // Then
        verify(outboxService).saveWithRouting(
                eq(event),
                eq("ReceiptPdfDocument"),
                eq(documentId),
                eq("pdf.generated.receipt"),
                eq(documentId),
                anyString() // headers JSON
        );
    }

    @Test
    @DisplayName("publishGenerated() includes documentType header")
    void testPublishGenerated_Headers() {
        // Given
        ReceiptPdfGeneratedEvent event = new ReceiptPdfGeneratedEvent(
                "saga-001", "doc-123", "RCP-001",
                "http://localhost:9000/receipts/test.pdf", 12345L, true, "corr-456");

        // When
        eventPublisher.publishGenerated(event);

        // Then
        ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(
                any(), anyString(), anyString(), anyString(), anyString(),
                headersCaptor.capture()
        );

        String headersJson = headersCaptor.getValue();
        assertThat(headersJson).contains("\"documentType\":\"RECEIPT\"");
        assertThat(headersJson).contains("\"correlationId\":\"corr-456\"");
    }

    @Test
    @DisplayName("ReceiptPdfGeneratedEvent stores sagaId and correlationId independently")
    void testSagaIdAndCorrelationIdStoredIndependently() throws Exception {
        // Given
        String sagaId = "saga-001";
        String correlationId = "corr-456";

        // When
        ReceiptPdfGeneratedEvent event = new ReceiptPdfGeneratedEvent(
                sagaId,
                "doc-123", "RCP-2024-001",
                "http://localhost:9000/receipts/test.pdf", 12345L, true,
                correlationId);

        // Then — constructor stores fields independently
        assertThat(event.getSagaId()).isEqualTo(sagaId);
        assertThat(event.getCorrelationId()).isEqualTo(correlationId);

        // And — JSON round-trip preserves both fields
        String json = objectMapper.writeValueAsString(event);
        ReceiptPdfGeneratedEvent deserialized = objectMapper.readValue(json, ReceiptPdfGeneratedEvent.class);
        assertThat(deserialized.getSagaId()).isEqualTo(sagaId);
        assertThat(deserialized.getCorrelationId()).isEqualTo(correlationId);
    }
}