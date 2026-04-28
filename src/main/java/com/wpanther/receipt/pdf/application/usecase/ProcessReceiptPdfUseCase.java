package com.wpanther.receipt.pdf.application.usecase;

import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.KafkaReceiptProcessCommand;

public interface ProcessReceiptPdfUseCase {
    void handle(KafkaReceiptProcessCommand command);
}
