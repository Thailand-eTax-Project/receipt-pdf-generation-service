package com.wpanther.receipt.pdf.application.service;

import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.receipt.pdf.application.dto.event.DocumentArchiveEvent;
import com.wpanther.receipt.pdf.application.dto.event.ReceiptPdfGeneratedEvent;
import com.wpanther.receipt.pdf.application.port.out.DocumentArchivePort;
import com.wpanther.receipt.pdf.application.port.out.PdfEventPort;
import com.wpanther.receipt.pdf.application.port.out.SagaReplyPort;
import com.wpanther.receipt.pdf.domain.model.GenerationStatus;
import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;
import com.wpanther.receipt.pdf.domain.repository.ReceiptPdfDocumentRepository;
import com.wpanther.receipt.pdf.domain.service.ReceiptPdfGenerationService;
import com.wpanther.receipt.pdf.infrastructure.metrics.PdfGenerationMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReceiptPdfDocumentService.
 *
 * After layer separation refactor:
 * - process() takes plain fields (documentId, documentNumber, signedXmlUrl, sagaId, sagaStep, correlationId)
 * - compensate() takes plain fields (documentId, sagaId, sagaStep, correlationId)
 * - publishIdempotentSuccess() takes plain fields
 * - publishRetryExhausted() takes plain fields
 * - publishGenerationFailure() takes plain fields
 * - publishCompensated() takes plain fields
 * - publishCompensationFailure() takes plain fields
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReceiptPdfDocumentService Unit Tests")
class ReceiptPdfDocumentServiceTest {

    @Mock
    private ReceiptPdfDocumentRepository repository;

    @Mock
    private PdfEventPort pdfEventPort;

    @Mock
    private SagaReplyPort sagaReplyPort;

    @Mock
    private DocumentArchivePort documentArchivePort;

    @Mock
    private PdfGenerationMetrics pdfGenerationMetrics;

    @Mock
    private com.wpanther.receipt.pdf.application.port.out.SignedXmlFetchPort signedXmlFetchPort;

    @Mock
    private ReceiptPdfGenerationService pdfGenerationService;

    @Mock
    private com.wpanther.receipt.pdf.application.port.out.PdfStoragePort pdfStoragePort;

    private ReceiptPdfDocumentService getService() {
        return new ReceiptPdfDocumentService(
                repository, pdfEventPort, sagaReplyPort, documentArchivePort,
                pdfGenerationMetrics, signedXmlFetchPort,
                pdfGenerationService, pdfStoragePort);
    }

    private ReceiptPdfDocument createCompletedDocument() {
        ReceiptPdfDocument doc = ReceiptPdfDocument.builder()
                .id(UUID.randomUUID())
                .receiptId("rcpt-inv-001")
                .receiptNumber("RCP-001")
                .status(GenerationStatus.COMPLETED)
                .documentPath("2024/01/15/test.pdf")
                .documentUrl("http://localhost:9000/receipts/test.pdf")
                .fileSize(5000L)
                .mimeType("application/pdf")
                .build();
        return doc;
    }

    @Test
    @DisplayName("findByReceiptId() delegates to repository")
    void testFindByReceiptId() {
        // Given
        ReceiptPdfDocument doc = createCompletedDocument();
        when(repository.findByReceiptId("rcpt-inv-001")).thenReturn(Optional.of(doc));

        // When
        var service = getService();
        Optional<ReceiptPdfDocument> result = service.findByReceiptId("rcpt-inv-001");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getReceiptNumber()).isEqualTo("RCP-001");
    }

    @Test
    @DisplayName("beginGeneration() creates GENERATING document")
    void testBeginGeneration() {
        // Given
        ReceiptPdfDocument savedDoc = ReceiptPdfDocument.builder()
                .id(UUID.randomUUID())
                .status(GenerationStatus.GENERATING)
                .receiptId("rcpt-inv-001")
                .receiptNumber("RCP-001")
                .mimeType("application/pdf")
                .build();
        when(repository.save(any())).thenReturn(savedDoc);

        // When
        var service = getService();
        service.beginGeneration("rcpt-inv-001", "RCP-001");

        // Then
        ArgumentCaptor<ReceiptPdfDocument> captor = ArgumentCaptor.forClass(ReceiptPdfDocument.class);
        verify(repository).save(captor.capture());

        ReceiptPdfDocument toSave = captor.getValue();
        assertThat(toSave.getReceiptId()).isEqualTo("rcpt-inv-001");
        assertThat(toSave.getReceiptNumber()).isEqualTo("RCP-001");
    }

    @Test
    @DisplayName("deleteById() deletes document and flushes")
    void testDeleteById() {
        // When
        var service = getService();
        service.deleteById(UUID.randomUUID());

        // Then
        verify(repository).deleteById(any(UUID.class));
        verify(repository).flush();
    }

    @Test
    @DisplayName("publishIdempotentSuccess() publishes events for already completed document (plain fields)")
    void testPublishIdempotentSuccess() {
        // Given
        ReceiptPdfDocument doc = createCompletedDocument();
        String documentId = "doc-1";
        String documentNumber = "RCP-001";
        String sagaId = "saga-1";
        SagaStep sagaStep = SagaStep.GENERATE_RECEIPT_PDF;
        String correlationId = "corr-1";

        // When
        var service = getService();
        service.publishIdempotentSuccess(doc, documentId, documentNumber, sagaId, sagaStep, correlationId);

        // Then
        verify(pdfEventPort).publishGenerated(any(ReceiptPdfGeneratedEvent.class));
        verify(sagaReplyPort).publishSuccess(sagaId, sagaStep, correlationId,
                "http://localhost:9000/receipts/test.pdf", 5000L);
    }

    @Test
    @DisplayName("publishRetryExhausted() publishes failure reply (plain fields)")
    void testPublishRetryExhausted() {
        // Given
        String sagaId = "saga-1";
        SagaStep sagaStep = SagaStep.GENERATE_RECEIPT_PDF;
        String correlationId = "corr-1";
        String documentId = "doc-1";
        String documentNumber = "RCP-001";

        // When
        var service = getService();
        service.publishRetryExhausted(sagaId, sagaStep, correlationId, documentId, documentNumber);

        // Then
        verify(sagaReplyPort).publishFailure(sagaId, sagaStep, correlationId,
                "Maximum retry attempts exceeded");
    }

    @Test
    @DisplayName("publishGenerationFailure() publishes failure with error message (plain fields)")
    void testPublishGenerationFailure() {
        // Given
        String sagaId = "saga-1";
        SagaStep sagaStep = SagaStep.GENERATE_RECEIPT_PDF;
        String correlationId = "corr-1";
        String errorMessage = "Invalid XML format";

        // When
        var service = getService();
        service.publishGenerationFailure(sagaId, sagaStep, correlationId, errorMessage);

        // Then
        verify(sagaReplyPort).publishFailure(sagaId, sagaStep, correlationId,
                errorMessage);
    }

    @Test
    @DisplayName("publishCompensated() publishes COMPENSATED reply (plain fields)")
    void testPublishCompensated() {
        // Given
        String sagaId = "saga-1";
        SagaStep sagaStep = SagaStep.GENERATE_RECEIPT_PDF;
        String correlationId = "corr-1";

        // When
        var service = getService();
        service.publishCompensated(sagaId, sagaStep, correlationId);

        // Then
        verify(sagaReplyPort).publishCompensated(sagaId, sagaStep, correlationId);
    }

    @Test
    @DisplayName("publishCompensationFailure() publishes failure for compensation error (plain fields)")
    void testPublishCompensationFailure() {
        // Given
        String sagaId = "saga-1";
        SagaStep sagaStep = SagaStep.GENERATE_RECEIPT_PDF;
        String correlationId = "corr-1";
        String error = "Failed to delete PDF file";

        // When
        var service = getService();
        service.publishCompensationFailure(sagaId, sagaStep, correlationId, error);

        // Then
        verify(sagaReplyPort).publishFailure(sagaId, sagaStep, correlationId, error);
    }

    @Test
    @DisplayName("completeGenerationAndPublish() marks COMPLETED and publishes events (plain fields)")
    void testCompleteGenerationAndPublish() {
        // Given
        UUID documentId = UUID.randomUUID();
        String documentPath = "2024/01/15/test.pdf";
        String fileUrl = "http://localhost:9000/receipts/test.pdf";
        long fileSize = 5000L;
        int previousRetryCount = 0;
        String documentIdParam = "doc-1";
        String documentNumber = "RCP-001";
        String sagaId = "saga-1";
        SagaStep sagaStep = SagaStep.GENERATE_RECEIPT_PDF;
        String correlationId = "corr-1";

        ReceiptPdfDocument doc = ReceiptPdfDocument.builder()
                .id(documentId)
                .receiptId("rcpt-inv-001")
                .receiptNumber("RCP-001")
                .status(GenerationStatus.GENERATING)
                .mimeType("application/pdf")
                .build();

        ReceiptPdfDocument savedDoc = ReceiptPdfDocument.builder()
                .id(documentId)
                .receiptId("rcpt-inv-001")
                .receiptNumber("RCP-001")
                .status(GenerationStatus.COMPLETED)
                .documentPath(documentPath)
                .documentUrl(fileUrl)
                .fileSize(fileSize)
                .mimeType("application/pdf")
                .xmlEmbedded(true)
                .build();

        when(repository.findById(documentId)).thenReturn(Optional.of(doc));
        when(repository.save(any())).thenReturn(savedDoc);

        // When
        var service = getService();
        service.completeGenerationAndPublish(documentId, documentPath, fileUrl, fileSize,
                previousRetryCount, documentIdParam, documentNumber, sagaId, sagaStep, correlationId);

        // Then
        ArgumentCaptor<ReceiptPdfDocument> captor = ArgumentCaptor.forClass(ReceiptPdfDocument.class);
        verify(repository).save(captor.capture());

        ReceiptPdfDocument saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(saved.isXmlEmbedded()).isTrue();

        verify(pdfEventPort).publishGenerated(any(ReceiptPdfGeneratedEvent.class));
        verify(sagaReplyPort).publishSuccess(sagaId, sagaStep, correlationId, fileUrl, fileSize);
    }

    @Test
    @DisplayName("failGenerationAndPublish() marks FAILED and publishes failure (plain fields)")
    void testFailGenerationAndPublish() {
        // Given
        UUID documentId = UUID.randomUUID();
        String errorMessage = "PDF generation failed";
        int previousRetryCount = 0;
        String documentIdParam = "doc-1";
        String documentNumber = "RCP-001";
        String sagaId = "saga-1";
        SagaStep sagaStep = SagaStep.GENERATE_RECEIPT_PDF;
        String correlationId = "corr-1";

        ReceiptPdfDocument doc = ReceiptPdfDocument.builder()
                .id(documentId)
                .receiptId("rcpt-inv-001")
                .receiptNumber("RCP-001")
                .status(GenerationStatus.GENERATING)
                .mimeType("application/pdf")
                .build();

        ReceiptPdfDocument savedDoc = ReceiptPdfDocument.builder()
                .id(documentId)
                .receiptId("rcpt-inv-001")
                .receiptNumber("RCP-001")
                .status(GenerationStatus.FAILED)
                .errorMessage(errorMessage)
                .mimeType("application/pdf")
                .build();

        when(repository.findById(documentId)).thenReturn(Optional.of(doc));
        when(repository.save(any())).thenReturn(savedDoc);

        // When
        var service = getService();
        service.failGenerationAndPublish(documentId, errorMessage, previousRetryCount,
                documentIdParam, documentNumber, sagaId, sagaStep, correlationId);

        // Then
        ArgumentCaptor<ReceiptPdfDocument> captor = ArgumentCaptor.forClass(ReceiptPdfDocument.class);
        verify(repository).save(captor.capture());

        ReceiptPdfDocument saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(saved.getErrorMessage()).isEqualTo(errorMessage);

        verify(sagaReplyPort).publishFailure(sagaId, sagaStep, correlationId, errorMessage);
    }
}