package com.wpanther.receipt.pdf.application.port.out;

import com.wpanther.saga.domain.enums.SagaStep;

public interface SagaReplyPort {

    void publishSuccess(String sagaId, SagaStep step, String correlationId,
                        String pdfUrl, long pdfSize);

    void publishFailure(String sagaId, SagaStep step, String correlationId,
                        String errorMessage);

    void publishCompensated(String sagaId, SagaStep step, String correlationId);
}
