package com.wpanther.receipt.pdf.application.port.out;

import com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging.ReceiptPdfGeneratedEvent;

public interface PdfEventPort {
    void publishGenerated(ReceiptPdfGeneratedEvent event);
}
