package com.wpanther.receipt.pdf.domain.service;

public interface ReceiptPdfGenerationService {

    byte[] generatePdf(String receiptNumber, String signedXml)
        throws ReceiptPdfGenerationException;

    class ReceiptPdfGenerationException extends Exception {
        public ReceiptPdfGenerationException(String message) { super(message); }
        public ReceiptPdfGenerationException(String message, Throwable cause) { super(message, cause); }
    }
}
