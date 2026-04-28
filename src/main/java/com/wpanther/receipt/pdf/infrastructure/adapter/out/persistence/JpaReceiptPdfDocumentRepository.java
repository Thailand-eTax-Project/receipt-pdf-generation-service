package com.wpanther.receipt.pdf.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface JpaReceiptPdfDocumentRepository extends JpaRepository<ReceiptPdfDocumentEntity, UUID> {

    Optional<ReceiptPdfDocumentEntity> findByReceiptId(String receiptId);

    @Query("SELECT e.documentPath FROM ReceiptPdfDocumentEntity e WHERE e.documentPath IS NOT NULL")
    Set<String> findAllDocumentPaths();
}
