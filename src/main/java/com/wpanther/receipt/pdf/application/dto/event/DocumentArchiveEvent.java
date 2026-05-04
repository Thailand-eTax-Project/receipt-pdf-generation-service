package com.wpanther.receipt.pdf.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class DocumentArchiveEvent extends TraceEvent {

    private static final String EVENT_TYPE = "DocumentArchiveEvent";
    private static final String SOURCE = "receipt-pdf-generation-service";
    private static final String TRACE_TYPE = "DOCUMENT_ARCHIVE";

    @JsonProperty("documentId")        private final String documentId;
    @JsonProperty("documentNumber")    private final String documentNumber;
    @JsonProperty("documentType")      private final String documentType;
    @JsonProperty("artifactType")      private final String artifactType;
    @JsonProperty("sourceUrl")         private final String sourceUrl;
    @JsonProperty("fileName")          private final String fileName;
    @JsonProperty("contentType")       private final String contentType;
    @JsonProperty("fileSize")          private final long fileSize;

    public DocumentArchiveEvent(
            String documentId, String documentNumber, String documentType,
            String artifactType, String sourceUrl, String fileName, String contentType, long fileSize,
            String sagaId, String correlationId) {
        super(sagaId, correlationId, SOURCE, TRACE_TYPE, null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
        this.artifactType = artifactType;
        this.sourceUrl = sourceUrl;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }

    @Override
    public String getEventType() { return EVENT_TYPE; }

    @JsonCreator
    public DocumentArchiveEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("documentNumber") String documentNumber,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("artifactType") String artifactType,
        @JsonProperty("sourceUrl") String sourceUrl,
        @JsonProperty("fileName") String fileName,
        @JsonProperty("contentType") String contentType,
        @JsonProperty("fileSize") long fileSize) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
        this.artifactType = artifactType;
        this.sourceUrl = sourceUrl;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }
}