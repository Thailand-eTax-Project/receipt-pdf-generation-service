package com.wpanther.receipt.pdf.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Inbound port for receipt PDF compensation.
 * Called by SagaCommandHandler with plain fields — no command objects.
 */
public interface CompensateReceiptPdfUseCase {

    void compensate(String documentId, String sagaId, SagaStep sagaStep, String correlationId);
}
