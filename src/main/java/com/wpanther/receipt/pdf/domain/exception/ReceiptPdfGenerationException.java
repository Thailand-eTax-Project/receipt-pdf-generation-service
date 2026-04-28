package com.wpanther.receipt.pdf.domain.exception;

public class ReceiptPdfGenerationException extends RuntimeException {

    public ReceiptPdfGenerationException(String message) {
        super(message);
    }

    public ReceiptPdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
