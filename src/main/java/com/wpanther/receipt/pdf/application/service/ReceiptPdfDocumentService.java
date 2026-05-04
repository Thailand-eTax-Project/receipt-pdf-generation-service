package com.wpanther.receipt.pdf.application.service;

import com.wpanther.receipt.pdf.application.dto.event.DocumentArchiveEvent;
import com.wpanther.receipt.pdf.application.dto.event.ReceiptPdfGeneratedEvent;
import com.wpanther.receipt.pdf.application.port.in.CompensateReceiptPdfUseCase;
import com.wpanther.receipt.pdf.application.port.in.ProcessReceiptPdfUseCase;
import com.wpanther.receipt.pdf.application.port.out.DocumentArchivePort;
import com.wpanther.receipt.pdf.application.port.out.PdfEventPort;
import com.wpanther.receipt.pdf.application.port.out.PdfStoragePort;
import com.wpanther.receipt.pdf.application.port.out.SagaReplyPort;
import com.wpanther.receipt.pdf.application.port.out.SignedXmlFetchPort;
import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;
import com.wpanther.receipt.pdf.domain.repository.ReceiptPdfDocumentRepository;
import com.wpanther.receipt.pdf.domain.service.ReceiptPdfGenerationService;
import com.wpanther.receipt.pdf.infrastructure.metrics.PdfGenerationMetrics;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ReceiptPdfDocumentService
        implements ProcessReceiptPdfUseCase, CompensateReceiptPdfUseCase {

    private static final int MAX_RETRIES = 3;

    private final ReceiptPdfDocumentRepository repository;
    private final PdfEventPort pdfEventPort;
    private final SagaReplyPort sagaReplyPort;
    private final DocumentArchivePort documentArchivePort;
    private final PdfGenerationMetrics pdfGenerationMetrics;
    private final SignedXmlFetchPort signedXmlFetchPort;
    private final ReceiptPdfGenerationService pdfGenerationService;
    private final PdfStoragePort pdfStoragePort;

    public ReceiptPdfDocumentService(
            ReceiptPdfDocumentRepository repository,
            PdfEventPort pdfEventPort,
            SagaReplyPort sagaReplyPort,
            DocumentArchivePort documentArchivePort,
            PdfGenerationMetrics pdfGenerationMetrics,
            SignedXmlFetchPort signedXmlFetchPort,
            ReceiptPdfGenerationService pdfGenerationService,
            PdfStoragePort pdfStoragePort
    ) {
        this.repository = repository;
        this.pdfEventPort = pdfEventPort;
        this.sagaReplyPort = sagaReplyPort;
        this.documentArchivePort = documentArchivePort;
        this.pdfGenerationMetrics = pdfGenerationMetrics;
        this.signedXmlFetchPort = signedXmlFetchPort;
        this.pdfGenerationService = pdfGenerationService;
        this.pdfStoragePort = pdfStoragePort;
    }

    // ─── ProcessReceiptPdfUseCase implementation ─────────────────────────────────────

    @Override
    public void process(String documentId, String documentNumber, String signedXmlUrl,
                        String sagaId, SagaStep sagaStep, String correlationId)
            throws ReceiptPdfGenerationException {
        MDC.put("sagaId", sagaId);
        MDC.put("documentId", documentId);
        MDC.put("correlationId", correlationId);

        try {
            // Validate input fields
            if (documentId == null || documentId.isBlank()
                    || documentNumber == null || documentNumber.isBlank()
                    || signedXmlUrl == null || signedXmlUrl.isBlank()) {
                failGenerationAndPublish(
                        null, "Missing required field: documentId, documentNumber, or signedXmlUrl",
                        0, documentId, documentNumber, sagaId, sagaStep, correlationId);
                throw new ReceiptPdfGenerationException(
                        "Missing required field: documentId, documentNumber, or signedXmlUrl");
            }

            // Idempotency check
            Optional<ReceiptPdfDocument> existingOpt = findByReceiptId(documentId);
            if (existingOpt.isPresent()) {
                ReceiptPdfDocument existing = existingOpt.get();
                if (existing.isCompleted()) {
                    publishIdempotentSuccess(existing, documentId, documentNumber, sagaId, sagaStep, correlationId);
                    return;
                }
                if (existing.getRetryCount() >= MAX_RETRIES) {
                    publishRetryExhausted(sagaId, sagaStep, correlationId, documentId, documentNumber);
                    return;
                }
            }

            // Begin generation
            ReceiptPdfDocument doc;
            int previousRetryCount = existingOpt.map(ReceiptPdfDocument::getRetryCount).orElse(0);
            if (existingOpt.isPresent()) {
                doc = replaceAndBeginGeneration(existingOpt.get().getId(), previousRetryCount, documentId, documentNumber);
            } else {
                doc = beginGeneration(documentId, documentNumber);
            }

            // Fetch signed XML
            String signedXml;
            try {
                signedXml = signedXmlFetchPort.fetch(signedXmlUrl);
            } catch (Exception e) {
                failGenerationAndPublish(doc.getId(), e.getMessage(), previousRetryCount,
                        documentId, documentNumber, sagaId, sagaStep, correlationId);
                throw new ReceiptPdfGenerationException("Failed to fetch signed XML: " + e.getMessage(), e);
            }

            // Generate PDF
            byte[] pdfBytes;
            try {
                pdfBytes = pdfGenerationService.generatePdf(documentNumber, signedXml);
            } catch (com.wpanther.receipt.pdf.domain.exception.ReceiptPdfGenerationException e) {
                failGenerationAndPublish(doc.getId(), e.getMessage(), previousRetryCount,
                        documentId, documentNumber, sagaId, sagaStep, correlationId);
                throw new ProcessReceiptPdfUseCase.ReceiptPdfGenerationException(e.getMessage(), e);
            }

            // Store PDF
            String documentPath;
            try {
                documentPath = pdfStoragePort.store(documentNumber, pdfBytes);
            } catch (Exception e) {
                failGenerationAndPublish(doc.getId(), "PDF storage failed: " + e.getMessage(), previousRetryCount,
                        documentId, documentNumber, sagaId, sagaStep, correlationId);
                throw new ReceiptPdfGenerationException("PDF storage failed: " + e.getMessage(), e);
            }

            String fileUrl = pdfStoragePort.resolveUrl(documentPath);
            long fileSize = pdfBytes.length;

            // Complete generation
            completeGenerationAndPublish(
                    doc.getId(), documentPath, fileUrl, fileSize,
                    previousRetryCount, documentId, documentNumber, sagaId, sagaStep, correlationId);

            log.info("Receipt PDF generated successfully for saga {} receipt {}", sagaId, documentNumber);

        } finally {
            MDC.remove("sagaId");
            MDC.remove("documentId");
            MDC.remove("correlationId");
        }
    }

    // ─── CompensateReceiptPdfUseCase implementation ──────────────────────────────────

    @Override
    public void compensate(String documentId, String sagaId, SagaStep sagaStep, String correlationId) {
        MDC.put("sagaId", sagaId);
        MDC.put("documentId", documentId);
        MDC.put("correlationId", correlationId);

        try {
            Optional<ReceiptPdfDocument> docOpt = findByReceiptId(documentId);
            if (docOpt.isEmpty()) {
                log.warn("ReceiptPdfDocument not found for compensation: receiptId={}", documentId);
                publishCompensated(sagaId, sagaStep, correlationId);
                return;
            }

            ReceiptPdfDocument doc = docOpt.get();
            try {
                // Delete PDF from storage
                if (doc.getDocumentPath() != null && !doc.getDocumentPath().isBlank()) {
                    pdfStoragePort.delete(doc.getDocumentPath());
                }
                // Delete document from repository
                deleteById(doc.getId());

                publishCompensated(sagaId, sagaStep, correlationId);
                log.info("Compensation completed for saga {} receipt {}", sagaId, doc.getReceiptNumber());

            } catch (Exception e) {
                publishCompensationFailure(sagaId, sagaStep, correlationId, e.getMessage());
                throw new RuntimeException("Compensation failed for document " + documentId + ": " + e.getMessage(), e);
            }

        } finally {
            MDC.remove("sagaId");
            MDC.remove("documentId");
            MDC.remove("correlationId");
        }
    }

    // ─── Existing domain methods ────────────────────────────────────────────────────

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
                                             String documentIdParam, String documentNumber,
                                             String sagaId, SagaStep sagaStep, String correlationId) {
        ReceiptPdfDocument doc = requireDocument(documentId);
        doc.markCompleted(s3Key, fileUrl, fileSize);
        doc.markXmlEmbedded();
        applyRetryCount(doc, previousRetryCount);
        doc = repository.save(doc);

        pdfEventPort.publishGenerated(buildGeneratedEvent(doc, documentIdParam, documentNumber, sagaId, correlationId));

        // Emit document.archive for unsigned PDF archival
        documentArchivePort.publish(new DocumentArchiveEvent(
                documentIdParam,
                doc.getReceiptNumber(),
                "RECEIPT",
                "UNSIGNED_PDF",
                doc.getDocumentUrl(),
                doc.getReceiptNumber() + ".pdf",
                "application/pdf",
                doc.getFileSize(),
                sagaId,
                correlationId));

        sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId, doc.getDocumentUrl(), doc.getFileSize());

        log.info("Completed PDF generation for saga {} receipt {}", sagaId, doc.getReceiptNumber());
    }

    @Transactional
    public void failGenerationAndPublish(UUID documentId, String errorMessage,
                                         int previousRetryCount,
                                         String documentIdParam, String documentNumber,
                                         String sagaId, SagaStep sagaStep, String correlationId) {
        String safeError = errorMessage != null ? errorMessage : "PDF generation failed";
        if (documentId != null) {
            ReceiptPdfDocument doc = requireDocument(documentId);
            doc.markFailed(safeError);
            applyRetryCount(doc, previousRetryCount);
            repository.save(doc);
        }
        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, safeError);
        log.warn("PDF generation failed for saga {} receipt {}: {}",
                sagaId, documentNumber, safeError);
    }

    @Transactional
    public void deleteById(UUID documentId) {
        repository.deleteById(documentId);
        repository.flush();
    }

    @Transactional
    public void publishIdempotentSuccess(ReceiptPdfDocument existing,
                                         String documentId, String documentNumber,
                                         String sagaId, SagaStep sagaStep, String correlationId) {
        pdfEventPort.publishGenerated(buildGeneratedEvent(existing, documentId, documentNumber, sagaId, correlationId));
        sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId, existing.getDocumentUrl(), existing.getFileSize());
        log.warn("Receipt PDF already generated for saga {} — re-publishing SUCCESS reply", sagaId);
    }

    @Transactional
    public void publishRetryExhausted(String sagaId, SagaStep sagaStep, String correlationId,
                                      String documentId, String documentNumber) {
        pdfGenerationMetrics.recordRetryExhausted(sagaId, documentId, documentNumber);
        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, "Maximum retry attempts exceeded");
        log.error("Max retries exceeded for saga {} document {}", sagaId, documentNumber);
    }

    @Transactional
    public void publishGenerationFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, errorMessage);
    }

    @Transactional
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {
        sagaReplyPort.publishCompensated(sagaId, sagaStep, correlationId);
    }

    @Transactional
    public void publishCompensationFailure(String sagaId, SagaStep sagaStep, String correlationId, String error) {
        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, error);
    }

    // ─── Helper methods ────────────────────────────────────────────────────────────

    private ReceiptPdfGeneratedEvent buildGeneratedEvent(ReceiptPdfDocument doc,
                                                          String documentId, String documentNumber,
                                                          String sagaId, String correlationId) {
        return new ReceiptPdfGeneratedEvent(
                sagaId,
                documentId,
                doc.getReceiptNumber(),
                doc.getDocumentUrl(),
                doc.getFileSize(),
                doc.isXmlEmbedded(),
                correlationId);
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