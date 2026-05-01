package com.wpanther.receipt.pdf.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.receipt.pdf.application.port.in.CompensateReceiptPdfUseCase;
import com.wpanther.receipt.pdf.application.port.in.ProcessReceiptPdfUseCase;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.SagaCommandHandler;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.SagaRouteConfig;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.CompensateReceiptPdfCommand;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.ProcessReceiptPdfCommand;
import com.wpanther.receipt.pdf.application.dto.event.ReceiptPdfGeneratedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Camel route configuration and Kafka command serialization.
 *
 * The new SagaRouteConfig receives Kafka messages and calls use case interfaces
 * directly with plain field parameters — no command objects flow into the domain.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CamelRouteConfig Unit Tests")
class CamelRouteConfigTest {

    @Mock
    private ProcessReceiptPdfUseCase processUseCase;

    @Mock
    private CompensateReceiptPdfUseCase compensateUseCase;

    @Mock
    private SagaCommandHandler sagaCommandHandler;

    private ObjectMapper objectMapper;
    private SagaRouteConfig sagaRouteConfig;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        sagaRouteConfig = new SagaRouteConfig(processUseCase, compensateUseCase, sagaCommandHandler, objectMapper);
    }

    @Test
    @DisplayName("Should serialize and deserialize ProcessReceiptPdfCommand")
    void testProcessReceiptPdfCommandSerialization() throws Exception {
        // Given
        ProcessReceiptPdfCommand command = new ProcessReceiptPdfCommand(
                "saga-001", SagaStep.GENERATE_RECEIPT_PDF, "corr-456",
                "doc-123", "RCP-2024-001",
                "http://minio/receipt-signed.xml"
        );

        // When
        String json = objectMapper.writeValueAsString(command);
        ProcessReceiptPdfCommand deserialized = objectMapper.readValue(json, ProcessReceiptPdfCommand.class);

        // Then
        assertThat(deserialized.getSagaId()).isEqualTo("saga-001");
        assertThat(deserialized.getSagaStep()).isEqualTo(SagaStep.GENERATE_RECEIPT_PDF);
        assertThat(deserialized.getCorrelationId()).isEqualTo("corr-456");
        assertThat(deserialized.getDocumentId()).isEqualTo("doc-123");
        assertThat(deserialized.getDocumentNumber()).isEqualTo("RCP-2024-001");
        assertThat(deserialized.getSignedXmlUrl()).isEqualTo("http://minio/receipt-signed.xml");
        assertThat(deserialized.getEventId()).isNotNull();
    }

    @Test
    @DisplayName("Should serialize and deserialize ReceiptCompensateCommand")
    void testCompensateReceiptPdfCommandSerialization() throws Exception {
        // Given
        CompensateReceiptPdfCommand command = new CompensateReceiptPdfCommand(
                "saga-001", SagaStep.GENERATE_RECEIPT_PDF, "corr-456",
                "doc-123"
        );

        // When
        String json = objectMapper.writeValueAsString(command);
        CompensateReceiptPdfCommand deserialized = objectMapper.readValue(json, CompensateReceiptPdfCommand.class);

        // Then
        assertThat(deserialized.getSagaId()).isEqualTo("saga-001");
        assertThat(deserialized.getSagaStep()).isEqualTo(SagaStep.GENERATE_RECEIPT_PDF);
        assertThat(deserialized.getCorrelationId()).isEqualTo("corr-456");
        assertThat(deserialized.getDocumentId()).isEqualTo("doc-123");
    }

    @Test
    @DisplayName("Should serialize and deserialize ReceiptPdfGeneratedEvent")
    void testReceiptPdfGeneratedEventSerialization() throws Exception {
        // Given
        ReceiptPdfGeneratedEvent event = new ReceiptPdfGeneratedEvent(
                "saga-001", "doc-123", "RCP-2024-001",
                "http://example.com/doc.pdf", 12345L, true, "corr-456"
        );

        // When
        String json = objectMapper.writeValueAsString(event);

        // Then
        assertThat(json).contains("\"eventType\":\"pdf.generated.receipt\"");
        assertThat(json).contains("\"eventId\"");
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Should deserialize ProcessReceiptPdfCommand from JSON with kebab-case sagaStep")
    void testProcessCommandDeserialization() throws Exception {
        // Given - sagaStep uses kebab-case code as serialized by SagaStep @JsonValue
        String json = """
            {
                "eventId": "550e8400-e29b-41d4-a716-446655440000",
                "occurredAt": "2024-01-15T10:30:00Z",
                "eventType": "saga.command.receipt-pdf",
                "version": 1,
                "sagaId": "saga-001",
                "sagaStep": "generate-receipt-pdf",
                "correlationId": "corr-456",
                "documentId": "doc-123",
                "documentNumber": "RCP-2024-001",
                "signedXmlUrl": "<Receipt>signed</Receipt>"
            }
            """;

        // When
        ProcessReceiptPdfCommand cmd = objectMapper.readValue(json, ProcessReceiptPdfCommand.class);

        // Then
        assertThat(cmd.getEventId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(cmd.getSagaId()).isEqualTo("saga-001");
        assertThat(cmd.getSagaStep()).isEqualTo(SagaStep.GENERATE_RECEIPT_PDF);
        assertThat(cmd.getCorrelationId()).isEqualTo("corr-456");
        assertThat(cmd.getDocumentId()).isEqualTo("doc-123");
        assertThat(cmd.getDocumentNumber()).isEqualTo("RCP-2024-001");
        assertThat(cmd.getSignedXmlUrl()).isEqualTo("<Receipt>signed</Receipt>");
    }
}