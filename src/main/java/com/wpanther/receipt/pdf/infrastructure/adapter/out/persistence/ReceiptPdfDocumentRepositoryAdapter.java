package com.wpanther.receipt.pdf.infrastructure.adapter.out.persistence;

import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;
import com.wpanther.receipt.pdf.domain.repository.ReceiptPdfDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReceiptPdfDocumentRepositoryAdapter implements ReceiptPdfDocumentRepository {

    private final JpaReceiptPdfDocumentRepository jpaRepository;

    @Override
    public ReceiptPdfDocument save(ReceiptPdfDocument document) {
        ReceiptPdfDocumentEntity entity = toEntity(document);
        entity = jpaRepository.save(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<ReceiptPdfDocument> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ReceiptPdfDocument> findByReceiptId(String receiptId) {
        return jpaRepository.findByReceiptId(receiptId).map(this::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void flush() {
        jpaRepository.flush();
    }

    private ReceiptPdfDocumentEntity toEntity(ReceiptPdfDocument document) {
        return ReceiptPdfDocumentEntity.builder()
            .id(document.getId())
            .receiptId(document.getReceiptId())
            .receiptNumber(document.getReceiptNumber())
            .documentPath(document.getDocumentPath())
            .documentUrl(document.getDocumentUrl())
            .fileSize(document.getFileSize())
            .mimeType(document.getMimeType())
            .xmlEmbedded(document.isXmlEmbedded())
            .status(document.getStatus())
            .errorMessage(document.getErrorMessage())
            .retryCount(document.getRetryCount())
            .createdAt(document.getCreatedAt())
            .completedAt(document.getCompletedAt())
            .build();
    }

    private ReceiptPdfDocument toDomain(ReceiptPdfDocumentEntity entity) {
        return ReceiptPdfDocument.builder()
            .id(entity.getId())
            .receiptId(entity.getReceiptId())
            .receiptNumber(entity.getReceiptNumber())
            .documentPath(entity.getDocumentPath())
            .documentUrl(entity.getDocumentUrl())
            .fileSize(entity.getFileSize() != null ? entity.getFileSize() : 0L)
            .mimeType(entity.getMimeType())
            .xmlEmbedded(entity.getXmlEmbedded() != null && entity.getXmlEmbedded())
            .status(entity.getStatus())
            .errorMessage(entity.getErrorMessage())
            .retryCount(entity.getRetryCount() != null ? entity.getRetryCount() : 0)
            .createdAt(entity.getCreatedAt())
            .completedAt(entity.getCompletedAt())
            .build();
    }
}
