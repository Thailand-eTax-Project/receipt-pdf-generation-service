package com.wpanther.receipt.pdf.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReceiptPdfGenerationException Tests")
class ReceiptPdfGenerationExceptionTest {

    @Test
    @DisplayName("Should create with message")
    void shouldCreateWithMessage() {
        ReceiptPdfGenerationException ex = new ReceiptPdfGenerationException("error");
        assertThat(ex.getMessage()).isEqualTo("error");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create with message and cause")
    void shouldCreateWithMessageAndCause() {
        Throwable cause = new RuntimeException("root");
        ReceiptPdfGenerationException ex = new ReceiptPdfGenerationException("error", cause);
        assertThat(ex.getMessage()).isEqualTo("error");
        assertThat(ex.getCause()).isEqualTo(cause);
    }
}
