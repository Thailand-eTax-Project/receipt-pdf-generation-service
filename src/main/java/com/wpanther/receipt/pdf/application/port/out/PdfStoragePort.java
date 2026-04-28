package com.wpanther.receipt.pdf.application.port.out;

public interface PdfStoragePort {

    String store(String receiptNumber, byte[] pdfBytes);

    void delete(String documentPath);

    String resolveUrl(String documentPath);
}
