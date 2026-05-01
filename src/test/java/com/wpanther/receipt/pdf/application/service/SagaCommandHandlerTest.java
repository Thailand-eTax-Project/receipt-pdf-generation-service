package com.wpanther.receipt.pdf.application.service;

import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.SagaCommandHandler;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.ReceiptCompensateCommand;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.ReceiptProcessCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the thin SagaCommandHandler adapter.
 *
 * The NEW SagaCommandHandler is a thin driving adapter that only routes
 * DTOs to the ReceiptPdfDocumentService.process()/compensate() methods with
 * plain field parameters. It does NOT do orchestration (beginGeneration,
 * completeGenerationAndPublish, etc.) - that logic lives in the service.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SagaCommandHandler Unit Tests (thin adapter)")
class SagaCommandHandlerTest {

    @Mock
    private ReceiptPdfDocumentService pdfDocumentService;

    private SagaCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SagaCommandHandler(pdfDocumentService);
    }

    // ─── handleProcessCommand tests ─────────────────────────────────────────────

    @Test
    @DisplayName("handleProcessCommand() delegates to process() with correct plain fields")
    void testHandleProcessCommand_DelegatesToProcess() throws Exception {
        // Given
        ReceiptProcessCommand command = new ReceiptProcessCommand(
                "saga-001", SagaStep.GENERATE_RECEIPT_PDF, "corr-456",
                "doc-123", "RCP-2024-001",
                "http://minio:9000/signed/receipt-signed.xml"
        );

        // When
        handler.handleProcessCommand(command);

        // Then — verify process() was called with correct plain field values
        verify(pdfDocumentService).process(
                eq("doc-123"),
                eq("RCP-2024-001"),
                eq("http://minio:9000/signed/receipt-signed.xml"),
                eq("saga-001"),
                eq(SagaStep.GENERATE_RECEIPT_PDF),
                eq("corr-456")
        );
    }

    @Test
    @DisplayName("handleProcessCommand() catches ReceiptPdfGenerationException and returns normally")
    void testHandleProcessCommand_ExceptionCaught() throws Exception {
        // Given
        ReceiptProcessCommand command = new ReceiptProcessCommand(
                "saga-001", SagaStep.GENERATE_RECEIPT_PDF, "corr-456",
                "doc-123", "RCP-2024-001",
                "http://minio:9000/signed/receipt-signed.xml"
        );

        doThrow(new com.wpanther.receipt.pdf.application.port.in.ProcessReceiptPdfUseCase.ReceiptPdfGenerationException("generation failed"))
                .when(pdfDocumentService).process(any(), any(), any(), any(), any(), any());

        // When — should not throw (exception is caught internally)
        handler.handleProcessCommand(command);

        // Then — verify process() was called once
        verify(pdfDocumentService, times(1)).process(any(), any(), any(), any(), any(), any());
    }

    // ─── handleCompensation tests ──────────────────────────────────────────────

    @Test
    @DisplayName("handleCompensation() delegates to compensate() with plain fields")
    void testHandleCompensation_DelegatesToCompensate() {
        // Given
        ReceiptCompensateCommand command = new ReceiptCompensateCommand(
                "saga-001", SagaStep.GENERATE_RECEIPT_PDF, "corr-456",
                "doc-123"
        );

        // When
        handler.handleCompensation(command);

        // Then
        verify(pdfDocumentService).compensate(
                eq("doc-123"),
                eq("saga-001"),
                eq(SagaStep.GENERATE_RECEIPT_PDF),
                eq("corr-456")
        );
    }

    // ─── publishOrchestrationFailure tests ───────────────────────────────────

    @Test
    @DisplayName("publishOrchestrationFailure() calls service with plain fields")
    void testPublishOrchestrationFailure() {
        // Given
        String sagaId = "saga-001";
        SagaStep sagaStep = SagaStep.GENERATE_RECEIPT_PDF;
        String correlationId = "corr-456";
        Throwable cause = new RuntimeException("DLQ error");

        // When
        handler.publishOrchestrationFailure(sagaId, sagaStep, correlationId, cause);

        // Then
        verify(pdfDocumentService).publishGenerationFailure(
                eq(sagaId),
                eq(sagaStep),
                eq(correlationId),
                anyString()
        );
    }

    @Test
    @DisplayName("publishCompensationOrchestrationFailure() calls service with plain fields")
    void testPublishCompensationOrchestrationFailure() {
        // Given
        String sagaId = "saga-001";
        SagaStep sagaStep = SagaStep.GENERATE_RECEIPT_PDF;
        String correlationId = "corr-456";
        Throwable cause = new RuntimeException("Compensation DLQ error");

        // When
        handler.publishCompensationOrchestrationFailure(sagaId, sagaStep, correlationId, cause);

        // Then
        verify(pdfDocumentService).publishCompensationFailure(
                eq(sagaId),
                eq(sagaStep),
                eq(correlationId),
                anyString()
        );
    }

    @Test
    @DisplayName("publishOrchestrationFailure() catches exceptions and logs (does not propagate)")
    void testPublishOrchestrationFailure_ExceptionHandled() {
        // Given
        doThrow(new RuntimeException("DB error"))
                .when(pdfDocumentService).publishGenerationFailure(any(), any(), any(), any());

        // When — should not throw
        handler.publishOrchestrationFailure("saga-001", SagaStep.GENERATE_RECEIPT_PDF, "corr-456",
                new RuntimeException("cause"));

        // Then — verify the service was called
        verify(pdfDocumentService).publishGenerationFailure(
                eq("saga-001"),
                eq(SagaStep.GENERATE_RECEIPT_PDF),
                eq("corr-456"),
                anyString()
        );
    }
}