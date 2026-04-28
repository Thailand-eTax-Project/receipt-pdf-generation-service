package com.wpanther.receipt.pdf.domain.service;

import com.wpanther.receipt.pdf.domain.exception.ReceiptPdfGenerationException;

public interface ReceiptPdfGenerationService {

    byte[] generatePdf(String receiptNumber, String signedXml)
        throws ReceiptPdfGenerationException;
}
