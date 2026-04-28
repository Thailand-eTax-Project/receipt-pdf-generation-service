package com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging;

import java.util.UUID;

public record ReceiptPdfGeneratedEvent(
    String sagaId,
    String documentId,
    String receiptNumber,
    String documentUrl,
    long fileSize,
    boolean xmlEmbedded,
    String correlationId
) {}
