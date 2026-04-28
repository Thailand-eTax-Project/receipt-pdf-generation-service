package com.wpanther.receipt.pdf.domain.model;

import com.wpanther.receipt.pdf.domain.exception.ReceiptPdfGenerationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class ReceiptPdfDocument {

    private static final String DEFAULT_MIME_TYPE = "application/pdf";

    private final UUID id;
    private final String receiptId;
    private final String receiptNumber;
    private String documentPath;
    private String documentUrl;
    private long fileSize;
    private final String mimeType;
    private boolean xmlEmbedded;
    private GenerationStatus status;
    private String errorMessage;
    private int retryCount;
    private final LocalDateTime createdAt;
    private LocalDateTime completedAt;

    private ReceiptPdfDocument(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.receiptId = Objects.requireNonNull(builder.receiptId, "Receipt ID is required");
        this.receiptNumber = Objects.requireNonNull(builder.receiptNumber, "Receipt number is required");
        this.documentPath = builder.documentPath;
        this.documentUrl = builder.documentUrl;
        this.fileSize = builder.fileSize;
        this.mimeType = builder.mimeType != null ? builder.mimeType : DEFAULT_MIME_TYPE;
        this.xmlEmbedded = builder.xmlEmbedded;
        this.status = builder.status != null ? builder.status : GenerationStatus.PENDING;
        this.errorMessage = builder.errorMessage;
        this.retryCount = builder.retryCount;
        this.createdAt = builder.createdAt != null ? builder.createdAt : LocalDateTime.now();
        this.completedAt = builder.completedAt;

        validateInvariant();
    }

    private void validateInvariant() {
        if (receiptId.isBlank()) {
            throw new ReceiptPdfGenerationException("Receipt ID cannot be blank");
        }
        if (receiptNumber.isBlank()) {
            throw new ReceiptPdfGenerationException("Receipt number cannot be blank");
        }
    }

    public void startGeneration() {
        if (this.status != GenerationStatus.PENDING) {
            throw new ReceiptPdfGenerationException("Can only start generation from PENDING status");
        }
        this.status = GenerationStatus.GENERATING;
    }

    public void markCompleted(String documentPath, String documentUrl, long fileSize) {
        if (this.status != GenerationStatus.GENERATING) {
            throw new ReceiptPdfGenerationException("Can only complete from GENERATING status");
        }
        Objects.requireNonNull(documentPath, "Document path is required");
        Objects.requireNonNull(documentUrl, "Document URL is required");
        if (fileSize <= 0) {
            throw new IllegalArgumentException("File size must be positive");
        }
        this.documentPath = documentPath;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.status = GenerationStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = GenerationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    public void markXmlEmbedded() {
        this.xmlEmbedded = true;
    }

    public boolean isSuccessful() { return status == GenerationStatus.COMPLETED; }
    public boolean isCompleted() { return status == GenerationStatus.COMPLETED; }
    public boolean isFailed() { return status == GenerationStatus.FAILED; }

    public void incrementRetryCount() { this.retryCount++; }

    public void incrementRetryCountTo(int target) {
        if (target < 0) throw new IllegalArgumentException("Target retry count cannot be negative");
        if (this.retryCount < target) this.retryCount = target;
    }

    public void setRetryCount(int retryCount) {
        if (retryCount < 0) throw new IllegalArgumentException("Retry count cannot be negative");
        this.retryCount = retryCount;
    }

    public boolean isMaxRetriesExceeded(int maxRetries) {
        return this.retryCount >= maxRetries;
    }

    // Getters
    public UUID getId() { return id; }
    public String getReceiptId() { return receiptId; }
    public String getReceiptNumber() { return receiptNumber; }
    public String getDocumentPath() { return documentPath; }
    public String getDocumentUrl() { return documentUrl; }
    public long getFileSize() { return fileSize; }
    public String getMimeType() { return mimeType; }
    public boolean isXmlEmbedded() { return xmlEmbedded; }
    public GenerationStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public int getRetryCount() { return retryCount; }

    @Override
    public String toString() {
        return "ReceiptPdfDocument{id=" + id + ", receiptId='" + receiptId + "', receiptNumber='" + receiptNumber + "', status=" + status + ", retryCount=" + retryCount + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReceiptPdfDocument other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private UUID id;
        private String receiptId;
        private String receiptNumber;
        private String documentPath;
        private String documentUrl;
        private long fileSize;
        private String mimeType;
        private boolean xmlEmbedded;
        private GenerationStatus status;
        private String errorMessage;
        private int retryCount;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder receiptId(String receiptId) { this.receiptId = receiptId; return this; }
        public Builder receiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; return this; }
        public Builder documentPath(String documentPath) { this.documentPath = documentPath; return this; }
        public Builder documentUrl(String documentUrl) { this.documentUrl = documentUrl; return this; }
        public Builder fileSize(long fileSize) { this.fileSize = fileSize; return this; }
        public Builder mimeType(String mimeType) { this.mimeType = mimeType; return this; }
        public Builder xmlEmbedded(boolean xmlEmbedded) { this.xmlEmbedded = xmlEmbedded; return this; }
        public Builder status(GenerationStatus status) { this.status = status; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder retryCount(int retryCount) { this.retryCount = retryCount; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder completedAt(LocalDateTime completedAt) { this.completedAt = completedAt; return this; }
        public ReceiptPdfDocument build() { return new ReceiptPdfDocument(this); }
    }
}
