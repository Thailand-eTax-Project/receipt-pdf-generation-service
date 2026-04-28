package com.wpanther.receipt.pdf.infrastructure.adapter.out.pdf;

import com.wpanther.receipt.pdf.domain.exception.ReceiptPdfGenerationException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for PdfA3Converter PDF/A-3 conversion functionality.
 *
 * <p>These tests verify basic converter behavior. Full PDF/A-3 compliance
 * testing requires specialized validation tools.</p>
 */
@DisplayName("PdfA3Converter Unit Tests")
class PdfA3ConverterTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("Constructor creates converter instance")
    void constructor_createsInstance() {
        PdfA3Converter converter = new PdfA3Converter("icc/sRGB.icc", meterRegistry);
        assertThat(converter).isNotNull();
    }

    @Test
    @DisplayName("convertToPdfA3() throws ReceiptPdfGenerationException for null input")
    void testConvertToPdfA3_NullInput_Throws() {
        PdfA3Converter converter = new PdfA3Converter("icc/sRGB.icc", meterRegistry);

        assertThatThrownBy(() ->
                converter.convertToPdfA3(null, "<xml/>", "test.xml", "RCPT-001"))
                .isInstanceOf(ReceiptPdfGenerationException.class);
    }

    @Test
    @DisplayName("convertToPdfA3() throws ReceiptPdfGenerationException for empty PDF bytes")
    void testConvertToPdfA3_EmptyPdf_Throws() {
        PdfA3Converter converter = new PdfA3Converter("icc/sRGB.icc", meterRegistry);

        assertThatThrownBy(() ->
                converter.convertToPdfA3(new byte[0], "<xml/>", "test.xml", "RCPT-001"))
                .isInstanceOf(ReceiptPdfGenerationException.class);
    }
}
