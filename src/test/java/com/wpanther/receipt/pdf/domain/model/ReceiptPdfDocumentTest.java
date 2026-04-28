package com.wpanther.receipt.pdf.domain.model;

import com.wpanther.receipt.pdf.domain.exception.ReceiptPdfGenerationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ReceiptPdfDocument Unit Tests")
class ReceiptPdfDocumentTest {

    private ReceiptPdfDocument.Builder defaultBuilder() {
        return ReceiptPdfDocument.builder()
                .receiptId("doc-123")
                .receiptNumber("RCP-2024-001");
    }

    @Nested
    @DisplayName("Builder and Creation")
    class BuilderTests {

        @Test
        @DisplayName("Should create document with required fields")
        void shouldCreateWithRequiredFields() {
            ReceiptPdfDocument doc = defaultBuilder().build();

            assertThat(doc.getId()).isNotNull();
            assertThat(doc.getReceiptId()).isEqualTo("doc-123");
            assertThat(doc.getReceiptNumber()).isEqualTo("RCP-2024-001");
            assertThat(doc.getStatus()).isEqualTo(GenerationStatus.PENDING);
            assertThat(doc.getFileSize()).isZero();
            assertThat(doc.getRetryCount()).isZero();
            assertThat(doc.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should throw when receiptId is null")
        void shouldThrowWhenReceiptIdNull() {
            assertThatThrownBy(() -> ReceiptPdfDocument.builder()
                    .receiptId(null)
                    .receiptNumber("RCP-001")
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Receipt ID is required");
        }

        @Test
        @DisplayName("Should throw when receiptId is blank")
        void shouldThrowWhenReceiptIdBlank() {
            assertThatThrownBy(() -> ReceiptPdfDocument.builder()
                    .receiptId("  ")
                    .receiptNumber("RCP-001")
                    .build())
                    .isInstanceOf(ReceiptPdfGenerationException.class)
                    .hasMessageContaining("Receipt ID cannot be blank");
        }

        @Test
        @DisplayName("Should throw when receiptNumber is null")
        void shouldThrowWhenReceiptNumberNull() {
            assertThatThrownBy(() -> ReceiptPdfDocument.builder()
                    .receiptId("doc-123")
                    .receiptNumber(null)
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Receipt number is required");
        }

        @Test
        @DisplayName("Should throw when receiptNumber is blank")
        void shouldThrowWhenReceiptNumberBlank() {
            assertThatThrownBy(() -> ReceiptPdfDocument.builder()
                    .receiptId("doc-123")
                    .receiptNumber("  ")
                    .build())
                    .isInstanceOf(ReceiptPdfGenerationException.class)
                    .hasMessageContaining("Receipt number cannot be blank");
        }

        @Test
        @DisplayName("Should use provided UUID when set")
        void shouldUseProvidedUuid() {
            UUID id = UUID.randomUUID();
            ReceiptPdfDocument doc = defaultBuilder().id(id).build();
            assertThat(doc.getId()).isEqualTo(id);
        }
    }

    @Nested
    @DisplayName("State Transitions")
    class StateTransitionTests {

        @Test
        @DisplayName("PENDING -> GENERATING via startGeneration()")
        void shouldTransitionToGenerating() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.startGeneration();
            assertThat(doc.getStatus()).isEqualTo(GenerationStatus.GENERATING);
        }

        @Test
        @DisplayName("Cannot startGeneration from GENERATING")
        void shouldNotStartFromGenerating() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.startGeneration();
            assertThatThrownBy(doc::startGeneration)
                    .isInstanceOf(ReceiptPdfGenerationException.class);
        }

        @Test
        @DisplayName("GENERATING -> COMPLETED via markCompleted()")
        void shouldTransitionToCompleted() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.startGeneration();
            doc.markCompleted("path/receipt.pdf", "http://minio/receipt.pdf", 12345L);

            assertThat(doc.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
            assertThat(doc.getDocumentPath()).isEqualTo("path/receipt.pdf");
            assertThat(doc.getDocumentUrl()).isEqualTo("http://minio/receipt.pdf");
            assertThat(doc.getFileSize()).isEqualTo(12345L);
            assertThat(doc.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("Cannot markCompleted from PENDING")
        void shouldNotCompleteFromPending() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            assertThatThrownBy(() -> doc.markCompleted("p", "u", 1L))
                    .isInstanceOf(ReceiptPdfGenerationException.class);
        }

        @Test
        @DisplayName("markCompleted requires non-null path")
        void shouldNotCompleteWithNullPath() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.startGeneration();
            assertThatThrownBy(() -> doc.markCompleted(null, "url", 1L))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("markCompleted requires positive fileSize")
        void shouldNotCompleteWithZeroSize() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.startGeneration();
            assertThatThrownBy(() -> doc.markCompleted("p", "u", 0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("any -> FAILED via markFailed()")
        void shouldTransitionToFailed() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.markFailed("Something went wrong");

            assertThat(doc.getStatus()).isEqualTo(GenerationStatus.FAILED);
            assertThat(doc.getErrorMessage()).isEqualTo("Something went wrong");
            assertThat(doc.isFailed()).isTrue();
            assertThat(doc.isCompleted()).isFalse();
            assertThat(doc.isSuccessful()).isFalse();
        }

        @Test
        @DisplayName("markFailed from GENERATING")
        void shouldFailFromGenerating() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.startGeneration();
            doc.markFailed("FOP error");
            assertThat(doc.getStatus()).isEqualTo(GenerationStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("Retry Tracking")
    class RetryTests {

        @Test
        @DisplayName("incrementRetryCount increments by one")
        void shouldIncrementRetry() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            assertThat(doc.getRetryCount()).isZero();
            doc.incrementRetryCount();
            assertThat(doc.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("incrementRetryCountTo sets to target if higher")
        void shouldAdvanceToTarget() {
            ReceiptPdfDocument doc = defaultBuilder().retryCount(2).build();
            doc.incrementRetryCountTo(5);
            assertThat(doc.getRetryCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("incrementRetryCountTo does not decrease")
        void shouldNotDecrease() {
            ReceiptPdfDocument doc = defaultBuilder().retryCount(5).build();
            doc.incrementRetryCountTo(3);
            assertThat(doc.getRetryCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("setRetryCount sets exact value")
        void shouldSetExact() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.setRetryCount(7);
            assertThat(doc.getRetryCount()).isEqualTo(7);
        }

        @Test
        @DisplayName("isMaxRetriesExceeded returns true when equal")
        void maxRetriesEqual() {
            ReceiptPdfDocument doc = defaultBuilder().retryCount(3).build();
            assertThat(doc.isMaxRetriesExceeded(3)).isTrue();
        }

        @Test
        @DisplayName("isMaxRetriesExceeded returns false when under")
        void maxRetriesUnder() {
            ReceiptPdfDocument doc = defaultBuilder().retryCount(2).build();
            assertThat(doc.isMaxRetriesExceeded(3)).isFalse();
        }
    }

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("Equal by ID")
        void equalById() {
            UUID id = UUID.randomUUID();
            ReceiptPdfDocument a = defaultBuilder().id(id).build();
            ReceiptPdfDocument b = defaultBuilder().id(id).receiptNumber("DIFFERENT").build();
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("Not equal by different ID")
        void notEqualByDifferentId() {
            ReceiptPdfDocument a = defaultBuilder().build();
            ReceiptPdfDocument b = defaultBuilder().build();
            assertThat(a).isNotEqualTo(b);
        }
    }
}
