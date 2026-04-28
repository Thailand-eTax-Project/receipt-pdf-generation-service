package com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaReply;

/**
 * Saga reply event for receipt PDF generation service.
 * Published to Kafka topic: saga.reply.receipt-pdf
 *
 * SUCCESS replies include pdfUrl and pdfSize so the orchestrator
 * can forward the URL to subsequent steps.
 */
public class ReceiptPdfReplyEvent extends SagaReply {

    private static final long serialVersionUID = 1L;

    // Additional fields included in SUCCESS replies
    private String pdfUrl;
    private Long pdfSize;

    public static ReceiptPdfReplyEvent success(
            String sagaId, SagaStep sagaStep, String correlationId,
            String pdfUrl, Long pdfSize) {
        ReceiptPdfReplyEvent reply = new ReceiptPdfReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS);
        reply.pdfUrl = pdfUrl;
        reply.pdfSize = pdfSize;
        return reply;
    }

    public static ReceiptPdfReplyEvent failure(String sagaId, SagaStep sagaStep, String correlationId,
                                               String errorMessage) {
        return new ReceiptPdfReplyEvent(sagaId, sagaStep, correlationId, errorMessage);
    }

    public static ReceiptPdfReplyEvent compensated(String sagaId, SagaStep sagaStep, String correlationId) {
        return new ReceiptPdfReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED);
    }

    private ReceiptPdfReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, ReplyStatus status) {
        super(sagaId, sagaStep, correlationId, status);
    }

    private ReceiptPdfReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        super(sagaId, sagaStep, correlationId, errorMessage);
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public Long getPdfSize() {
        return pdfSize;
    }
}
