package com.wpanther.receipt.pdf.application.port.out;

import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;

public interface PdfEventPort {

    void publishGenerated(ReceiptPdfDocument document, String correlationId);
}
