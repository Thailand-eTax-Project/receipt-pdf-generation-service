package com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka;

import com.wpanther.receipt.pdf.application.port.in.CompensateReceiptPdfUseCase;
import com.wpanther.receipt.pdf.application.port.in.ProcessReceiptPdfUseCase;
import com.wpanther.receipt.pdf.application.service.ReceiptPdfDocumentService;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.CompensateReceiptPdfCommand;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.ProcessReceiptPdfCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga command handler — thin driving adapter that receives Kafka messages and calls use cases.
 * No command objects flow into domain or application layers — only plain field parameters.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final ReceiptPdfDocumentService pdfDocumentService;

    /**
     * Handle a ProcessReceiptPdfCommand from saga orchestrator.
     * Delegates to ReceiptPdfDocumentService.process() with plain fields.
     * Catches ReceiptPdfGenerationException after the saga reply has been committed to outbox,
     * returns normally so Camel commits the Kafka offset.
     */
    public void handleProcessCommand(ProcessReceiptPdfCommand command) {
        log.info("Handling ProcessReceiptPdfCommand for saga {} document {}",
                command.getSagaId(), command.getDocumentNumber());
        try {
            pdfDocumentService.process(
                    command.getDocumentId(),
                    command.getDocumentNumber(),
                    command.getSignedXmlUrl(),
                    command.getSagaId(),
                    command.getSagaStep(),
                    command.getCorrelationId()
            );
        } catch (ProcessReceiptPdfUseCase.ReceiptPdfGenerationException e) {
            log.error("Receipt PDF generation failed for saga {}: {}",
                    command.getSagaId(), e.toString());
        }
    }

    /**
     * Handle a ReceiptCompensateCommand from saga orchestrator.
     * Delegates to ReceiptPdfDocumentService.compensate() with plain fields.
     */
    public void handleCompensation(CompensateReceiptPdfCommand command) {
        log.info("Handling compensation for saga {} document {}",
                command.getSagaId(), command.getDocumentId());
        pdfDocumentService.compensate(
                command.getDocumentId(),
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOrchestrationFailure(String sagaId, SagaStep sagaStep, String correlationId, Throwable cause) {
        try {
            pdfDocumentService.publishGenerationFailure(
                    sagaId, sagaStep, correlationId,
                    "Message routed to DLQ after retry exhaustion: " + describeThrowable(cause));
        } catch (Exception e) {
            log.error("Cannot notify orchestrator of DLQ failure for saga {}", sagaId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishCompensationOrchestrationFailure(String sagaId, SagaStep sagaStep, String correlationId, Throwable cause) {
        try {
            pdfDocumentService.publishCompensationFailure(
                    sagaId, sagaStep, correlationId,
                    "Compensation DLQ after retry exhaustion: " + describeThrowable(cause));
        } catch (Exception e) {
            log.error("Cannot notify orchestrator of compensation DLQ failure for saga {}", sagaId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOrchestrationFailureForUnparsedMessage(String sagaId, SagaStep sagaStep, String correlationId, Throwable cause) {
        try {
            pdfDocumentService.publishGenerationFailure(
                    sagaId, sagaStep, correlationId,
                    "Message routed to DLQ after deserialization failure: " + describeThrowable(cause));
        } catch (Exception e) {
            log.error("Cannot notify orchestrator of DLQ deserialization failure for saga {} — orchestrator must timeout", sagaId, e);
        }
    }

    private String describeThrowable(Throwable t) {
        if (t == null) return "unknown error";
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
    }
}