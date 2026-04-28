package com.wpanther.receipt.pdf.application.usecase;

import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.KafkaReceiptCompensateCommand;

public interface CompensateReceiptPdfUseCase {
    void handle(KafkaReceiptCompensateCommand command);
}
