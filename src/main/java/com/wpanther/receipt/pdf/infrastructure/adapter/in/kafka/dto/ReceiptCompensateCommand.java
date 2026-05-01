package com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;

import java.time.Instant;
import java.util.UUID;

public class ReceiptCompensateCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonCreator
    public ReceiptCompensateCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId") String documentId) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = documentId;
    }

    public ReceiptCompensateCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                    String documentId) {
        super(sagaId, sagaStep, correlationId);
        this.documentId = documentId;
    }

    public String getDocumentId() { return documentId; }
}