package com.wpanther.receipt.pdf.application.service;

import com.wpanther.receipt.pdf.application.port.out.PdfEventPort;
import com.wpanther.receipt.pdf.application.port.out.PdfStoragePort;
import com.wpanther.receipt.pdf.application.port.out.SagaReplyPort;
import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;
import com.wpanther.receipt.pdf.domain.repository.ReceiptPdfDocumentRepository;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.KafkaReceiptCompensateCommand;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.KafkaReceiptProcessCommand;
import com.wpanther.receipt.pdf.application.dto.event.ReceiptPdfGeneratedEvent;
import com.wpanther.receipt.pdf.infrastructure.metrics.PdfGenerationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptPdfDocumentService {

    private final ReceiptPdfDocumentRepository repository;
    private final PdfEventPort pdfEventPort;
    private final SagaReplyPort sagaReplyPort;
    private final PdfGenerationMetrics pdfGenerationMetrics;

    @Transactional(readOnly = true)
    public Optional<ReceiptPdfDocument> findByReceiptId(String receiptId) {
        return repository.findByReceiptId(receiptId);
    }

    @Transactional
    public ReceiptPdfDocument beginGeneration(String receiptId, String receiptNumber) {
        log.info("Initiating PDF generation for receipt: {}", receiptNumber);
        ReceiptPdfDocument doc = ReceiptPdfDocument.builder()
                .receiptId(receiptId)
                .receiptNumber(receiptNumber)
                .build();
        doc.startGeneration();
        return repository.save(doc);
    }

    @Transactional
    public ReceiptPdfDocument replaceAndBeginGeneration(
            UUID existingId, int previousRetryCount, String receiptId, String receiptNumber) {
        log.info("Replacing document {} and re-starting generation for receipt: {}", existingId, receiptNumber);
        repository.deleteById(existingId);
        repository.flush();
        ReceiptPdfDocument doc = ReceiptPdfDocument.builder()
                .receiptId(receiptId)
                .receiptNumber(receiptNumber)
                .build();
        doc.startGeneration();
        doc.incrementRetryCountTo(previousRetryCount + 1);
        return repository.save(doc);
    }

    @Transactional
    public void completeGenerationAndPublish(UUID documentId, String s3Key, String fileUrl,
                                             long fileSize, int previousRetryCount,
                                             KafkaReceiptProcessCommand command) {
        ReceiptPdfDocument doc = requireDocument(documentId);
        doc.markCompleted(s3Key, fileUrl, fileSize);
        doc.markXmlEmbedded();
        applyRetryCount(doc, previousRetryCount);
        doc = repository.save(doc);

        pdfEventPort.publishGenerated(buildGeneratedEvent(doc, command));
        sagaReplyPort.publishSuccess(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
                doc.getDocumentUrl(), doc.getFileSize());

        log.info("Completed PDF generation for saga {} receipt {}",
                command.getSagaId(), doc.getReceiptNumber());
    }

    @Transactional
    public void failGenerationAndPublish(UUID documentId, String errorMessage,
                                         int previousRetryCount,
                                         KafkaReceiptProcessCommand command) {
        String safeError = errorMessage != null ? errorMessage : "PDF generation failed";
        ReceiptPdfDocument doc = requireDocument(documentId);
        doc.markFailed(safeError);
        applyRetryCount(doc, previousRetryCount);
        repository.save(doc);

        sagaReplyPort.publishFailure(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId(), safeError);

        log.warn("PDF generation failed for saga {} receipt {}: {}",
                command.getSagaId(), doc.getReceiptNumber(), safeError);
    }

    @Transactional
    public void deleteById(UUID documentId) {
        repository.deleteById(documentId);
        repository.flush();
    }

    @Transactional
    public void publishIdempotentSuccess(ReceiptPdfDocument existing,
                                         KafkaReceiptProcessCommand command) {
        pdfEventPort.publishGenerated(buildGeneratedEvent(existing, command));
        sagaReplyPort.publishSuccess(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
                existing.getDocumentUrl(), existing.getFileSize());
        log.warn("Receipt PDF already generated for saga {} — re-publishing SUCCESS reply",
                command.getSagaId());
    }

    @Transactional
    public void publishRetryExhausted(KafkaReceiptProcessCommand command) {
        pdfGenerationMetrics.recordRetryExhausted(
                command.getSagaId(),
                command.getDocumentId(),
                command.getDocumentNumber());
        sagaReplyPort.publishFailure(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
                "Maximum retry attempts exceeded");
        log.error("Max retries exceeded for saga {} document {}",
                command.getSagaId(), command.getDocumentNumber());
    }

    @Transactional
    public void publishGenerationFailure(KafkaReceiptProcessCommand command, String errorMessage) {
        sagaReplyPort.publishFailure(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId(), errorMessage);
    }

    @Transactional
    public void publishCompensated(KafkaReceiptCompensateCommand command) {
        sagaReplyPort.publishCompensated(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId());
    }

    @Transactional
    public void publishCompensationFailure(KafkaReceiptCompensateCommand command, String error) {
        sagaReplyPort.publishFailure(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId(), error);
    }

    private ReceiptPdfGeneratedEvent buildGeneratedEvent(ReceiptPdfDocument doc,
                                                         KafkaReceiptProcessCommand command) {
        return new ReceiptPdfGeneratedEvent(
                command.getSagaId(),
                command.getDocumentId(),
                doc.getReceiptNumber(),
                doc.getDocumentUrl(),
                doc.getFileSize(),
                doc.isXmlEmbedded(),
                command.getCorrelationId());
    }

    private ReceiptPdfDocument requireDocument(UUID documentId) {
        return repository.findById(documentId)
                .orElseThrow(() -> {
                    log.error("ReceiptPdfDocument not found for id={}", documentId);
                    return new IllegalStateException("Expected receipt PDF document is absent");
                });
    }

    private void applyRetryCount(ReceiptPdfDocument doc, int previousRetryCount) {
        if (previousRetryCount < 0) return;
        doc.incrementRetryCountTo(previousRetryCount + 1);
    }
}
