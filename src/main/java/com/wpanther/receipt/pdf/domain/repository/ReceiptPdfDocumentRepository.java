package com.wpanther.receipt.pdf.domain.repository;

import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptPdfDocumentRepository {

    ReceiptPdfDocument save(ReceiptPdfDocument document);

    Optional<ReceiptPdfDocument> findById(UUID id);

    Optional<ReceiptPdfDocument> findByReceiptId(String receiptId);

    void deleteById(UUID id);

    void flush();
}
