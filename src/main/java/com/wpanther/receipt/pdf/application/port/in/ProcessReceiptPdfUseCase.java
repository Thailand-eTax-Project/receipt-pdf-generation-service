package com.wpanther.receipt.pdf.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Inbound port for receipt PDF generation.
 * Called by SagaCommandHandler with plain fields — no command objects.
 */
public interface ProcessReceiptPdfUseCase {

    void process(String documentId, String documentNumber, String signedXmlUrl,
                String sagaId, SagaStep sagaStep, String correlationId) throws ReceiptPdfGenerationException;

    class ReceiptPdfGenerationException extends Exception {
        public ReceiptPdfGenerationException(String message) { super(message); }
        public ReceiptPdfGenerationException(String message, Throwable cause) { super(message, cause); }
    }
}