package com.wpanther.receipt.pdf.application.port.out;

import com.wpanther.receipt.pdf.application.dto.event.ReceiptPdfGeneratedEvent;

public interface PdfEventPort {
    void publishGenerated(ReceiptPdfGeneratedEvent event);
}
