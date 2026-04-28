package com.wpanther.receipt.pdf.application.service;

import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.receipt.pdf.application.port.out.PdfEventPort;
import com.wpanther.receipt.pdf.application.port.out.SagaReplyPort;
import com.wpanther.receipt.pdf.domain.model.GenerationStatus;
import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;
import com.wpanther.receipt.pdf.domain.repository.ReceiptPdfDocumentRepository;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.KafkaReceiptCompensateCommand;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.KafkaReceiptProcessCommand;
import com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging.ReceiptPdfGeneratedEvent;
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
    private PdfGenerationMetrics pdfGenerationMetrics;

    // Note: Using reflection to instantiate because Lombok @RequiredArgsConstructor
    // is scope=provided, not available during test compilation
    private ReceiptPdfDocumentService getService() {
        try {
            return ReceiptPdfDocumentService.class
                    .getDeclaredConstructor(ReceiptPdfDocumentRepository.class,
                                           PdfEventPort.class, SagaReplyPort.class,
                                           PdfGenerationMetrics.class)
                    .newInstance(repository, pdfEventPort, sagaReplyPort, pdfGenerationMetrics);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate ReceiptPdfDocumentService", e);
        }
    }

    private ReceiptPdfDocument createCompletedDocument() {
        ReceiptPdfDocument doc = ReceiptPdfDocument.builder()
                .id(UUID.randomUUID())
                .receiptId("rcpt-inv-001")
                .receiptNumber("RCP-001")
                .status(GenerationStatus.GENERATING) // Start in GENERATING
                .mimeType("application/pdf")
                .build();
        // Now transition to COMPLETED via the proper method
        doc.markCompleted("2024/01/15/test.pdf", "http://localhost:9000/receipts/test.pdf", 5000L);
        doc.markXmlEmbedded();
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
    @DisplayName("publishIdempotentSuccess() publishes events for already completed document")
    void testPublishIdempotentSuccess() {
        // Given
        ReceiptPdfDocument doc = createCompletedDocument();
        KafkaReceiptProcessCommand command = new KafkaReceiptProcessCommand(
                "saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1",
                "doc-1", "RCP-001",
                "http://minio:9000/signed.xml");

        // When
        var service = getService();
        service.publishIdempotentSuccess(doc, command);

        // Then
        verify(pdfEventPort).publishGenerated(any(ReceiptPdfGeneratedEvent.class));
        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1",
                "http://localhost:9000/receipts/test.pdf", 5000L);
    }

    @Test
    @DisplayName("publishRetryExhausted() publishes failure reply")
    void testPublishRetryExhausted() {
        // Given
        KafkaReceiptProcessCommand command = new KafkaReceiptProcessCommand(
                "saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1",
                "doc-1", "RCP-001",
                "http://minio:9000/signed.xml");

        // When
        var service = getService();
        service.publishRetryExhausted(command);

        // Then
        verify(sagaReplyPort).publishFailure("saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1",
                "Maximum retry attempts exceeded");
    }

    @Test
    @DisplayName("publishGenerationFailure() publishes failure with error message")
    void testPublishGenerationFailure() {
        // Given
        KafkaReceiptProcessCommand command = new KafkaReceiptProcessCommand(
                "saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1",
                "doc-1", "RCP-001",
                "http://minio:9000/signed.xml");
        String errorMessage = "Invalid XML format";

        // When
        var service = getService();
        service.publishGenerationFailure(command, errorMessage);

        // Then
        verify(sagaReplyPort).publishFailure("saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1",
                errorMessage);
    }

    @Test
    @DisplayName("publishCompensated() publishes COMPENSATED reply")
    void testPublishCompensated() {
        // Given
        KafkaReceiptCompensateCommand command = new KafkaReceiptCompensateCommand(
                "saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1",
                "doc-1");

        // When
        var service = getService();
        service.publishCompensated(command);

        // Then
        verify(sagaReplyPort).publishCompensated("saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1");
    }

    @Test
    @DisplayName("publishCompensationFailure() publishes failure for compensation error")
    void testPublishCompensationFailure() {
        // Given
        KafkaReceiptCompensateCommand command = new KafkaReceiptCompensateCommand(
                "saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1",
                "doc-1");
        String error = "Failed to delete PDF file";

        // When
        var service = getService();
        service.publishCompensationFailure(command, error);

        // Then
        verify(sagaReplyPort).publishFailure("saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1", error);
    }

    @Test
    @DisplayName("completeGenerationAndPublish() marks COMPLETED and publishes events")
    void testCompleteGenerationAndPublish() {
        // Given
        UUID documentId = UUID.randomUUID();
        ReceiptPdfDocument doc = ReceiptPdfDocument.builder()
                .id(documentId)
                .receiptId("rcpt-inv-001")
                .receiptNumber("RCP-001")
                .status(GenerationStatus.GENERATING)
                .mimeType("application/pdf")
                .build();

        // Create a completed document with the same ID for the mock return
        ReceiptPdfDocument savedDoc = ReceiptPdfDocument.builder()
                .id(documentId)
                .receiptId("rcpt-inv-001")
                .receiptNumber("RCP-001")
                .status(GenerationStatus.GENERATING)
                .mimeType("application/pdf")
                .build();
        savedDoc.markCompleted("2024/01/15/test.pdf", "http://localhost:9000/receipts/test.pdf", 5000L);
        savedDoc.markXmlEmbedded();

        when(repository.findById(documentId)).thenReturn(Optional.of(doc));
        when(repository.save(any())).thenReturn(savedDoc);

        KafkaReceiptProcessCommand command = new KafkaReceiptProcessCommand(
                "saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1",
                "doc-1", "RCP-001",
                "http://minio:9000/signed.xml");

        // When
        var service = getService();
        service.completeGenerationAndPublish(documentId, "2024/01/15/test.pdf",
                "http://localhost:9000/receipts/test.pdf", 5000L, 0, command);

        // Then
        ArgumentCaptor<ReceiptPdfDocument> captor = ArgumentCaptor.forClass(ReceiptPdfDocument.class);
        verify(repository).save(captor.capture());

        ReceiptPdfDocument saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(saved.isXmlEmbedded()).isTrue();

        verify(pdfEventPort).publishGenerated(any(ReceiptPdfGeneratedEvent.class));
        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1",
                "http://localhost:9000/receipts/test.pdf", 5000L);
    }

    @Test
    @DisplayName("failGenerationAndPublish() marks FAILED and publishes failure")
    void testFailGenerationAndPublish() {
        // Given
        UUID documentId = UUID.randomUUID();
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
                .errorMessage("PDF generation failed")
                .mimeType("application/pdf")
                .build();
        when(repository.findById(documentId)).thenReturn(Optional.of(doc));
        when(repository.save(any())).thenReturn(savedDoc);

        KafkaReceiptProcessCommand command = new KafkaReceiptProcessCommand(
                "saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1",
                "doc-1", "RCP-001",
                "http://minio:9000/signed.xml");
        String errorMessage = "PDF generation failed";

        // When
        var service = getService();
        service.failGenerationAndPublish(documentId, errorMessage, 0, command);

        // Then
        ArgumentCaptor<ReceiptPdfDocument> captor = ArgumentCaptor.forClass(ReceiptPdfDocument.class);
        verify(repository).save(captor.capture());

        ReceiptPdfDocument saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(saved.getErrorMessage()).isEqualTo(errorMessage);

        verify(sagaReplyPort).publishFailure("saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1", errorMessage);
    }
}
