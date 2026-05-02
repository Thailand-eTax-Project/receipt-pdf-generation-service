package com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.IntegrationEvent;
import com.wpanther.saga.domain.model.SagaReply;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
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
 * Unit tests for SagaReplyPublisher.
 *
 * After layer separation refactor, ReceiptPdfReplyEvent is a private static inner class
 * of SagaReplyPublisher. Tests should verify the SagaReplyPort interface methods
 * (publishSuccess, publishFailure, publishCompensated) and use ArgumentCaptor to
 * capture the inner ReceiptPdfReplyEvent passed to outboxService.saveWithRouting().
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SagaReplyPublisher Unit Tests")
class SagaReplyPublisherTest {

    @Mock
    private OutboxService outboxService;

    private ObjectMapper objectMapper;
    private SagaReplyPublisher sagaReplyPublisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        sagaReplyPublisher = new SagaReplyPublisher("saga.reply.receipt-pdf", outboxService, objectMapper);
    }

    @Test
    @DisplayName("publishSuccess() calls OutboxService with SUCCESS reply")
    void testPublishSuccess() {
        // Given
        String sagaId = "saga-123";
        SagaStep sagaStep = SagaStep.GENERATE_RECEIPT_PDF;
        String correlationId = "corr-456";
        String pdfUrl = "http://localhost:9000/receipts/test.pdf";
        long pdfSize = 12345L;

        // When
        sagaReplyPublisher.publishSuccess(sagaId, sagaStep, correlationId, pdfUrl, pdfSize);

        // Then
        verify(outboxService).saveWithRouting(
                any(),
                eq("ReceiptPdfDocument"),
                eq(sagaId),
                eq("saga.reply.receipt-pdf"),
                eq(sagaId),
                anyString() // headers JSON
        );
    }

    @Test
    @DisplayName("publishSuccess() includes pdfUrl and pdfSize in reply payload")
    void testPublishSuccess_ReplyPayload() {
        // Given
        String sagaId = "saga-1";
        String pdfUrl = "http://localhost:9000/receipts/test.pdf";
        long pdfSize = 12345L;

        // When
        sagaReplyPublisher.publishSuccess(sagaId, SagaStep.GENERATE_RECEIPT_PDF, "corr-1", pdfUrl, pdfSize);

        // Then — use ArgumentCaptor to capture the private ReceiptPdfReplyEvent subclass
        ArgumentCaptor<SagaReply> replyCaptor = ArgumentCaptor.forClass(SagaReply.class);
        verify(outboxService).saveWithRouting(
                replyCaptor.capture(),
                anyString(), anyString(), anyString(), anyString(),
                anyString()
        );

        SagaReply reply = replyCaptor.getValue();
        // ReceiptPdfReplyEvent is a private inner class, verify via reflection or behavior
        assertThat(reply).isNotNull();
        // Verify it's the correct type by checking it has pdfUrl getter
        assertThat(invokeQuietly(reply, "getPdfUrl")).isEqualTo(pdfUrl);
        assertThat(invokeQuietly(reply, "getPdfSize")).isEqualTo(pdfSize);
    }

    @Test
    @DisplayName("publishFailure() calls OutboxService with FAILURE reply")
    void testPublishFailure() {
        // Given
        String sagaId = "saga-123";
        SagaStep sagaStep = SagaStep.GENERATE_RECEIPT_PDF;
        String correlationId = "corr-456";
        String errorMessage = "PDF generation failed";

        // When
        sagaReplyPublisher.publishFailure(sagaId, sagaStep, correlationId, errorMessage);

        // Then
        ArgumentCaptor<SagaReply> replyCaptor = ArgumentCaptor.forClass(SagaReply.class);
        verify(outboxService).saveWithRouting(
                replyCaptor.capture(),
                anyString(), anyString(), anyString(), anyString(),
                anyString()
        );

        SagaReply reply = replyCaptor.getValue();
        assertThat(reply).isNotNull();
        assertThat(invokeQuietly(reply, "getErrorMessage")).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("publishCompensated() calls OutboxService with COMPENSATED reply")
    void testPublishCompensated() {
        // Given
        String sagaId = "saga-123";
        SagaStep sagaStep = SagaStep.GENERATE_RECEIPT_PDF;
        String correlationId = "corr-456";

        // When
        sagaReplyPublisher.publishCompensated(sagaId, sagaStep, correlationId);

        // Then
        ArgumentCaptor<SagaReply> replyCaptor = ArgumentCaptor.forClass(SagaReply.class);
        verify(outboxService).saveWithRouting(
                replyCaptor.capture(),
                anyString(), anyString(), anyString(), anyString(),
                anyString()
        );

        SagaReply reply = replyCaptor.getValue();
        assertThat(reply).isNotNull();
        assertThat(reply.getStatus().name()).isEqualTo("COMPENSATED");
    }

    /**
     * Quietly invoke a method via reflection, returning null if it fails.
     * Uses getMethod() to find inherited methods as well.
     */
    private Object invokeQuietly(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Exception e) {
            return null;
        }
    }
}