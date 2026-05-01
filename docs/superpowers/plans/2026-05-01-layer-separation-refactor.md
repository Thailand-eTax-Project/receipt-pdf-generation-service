# receipt-pdf-generation-service Layer Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor receipt-pdf-generation-service to place saga infrastructure types in `infrastructure/adapter/in/kafka/dto/` and use case interfaces with plain parameters in `application/port/in/`, matching the architecture of taxinvoice-processing-service (the reference implementation).

**Architecture:** Kafka DTOs (SagaCommand + Jackson) live in `infrastructure/adapter/in/kafka/dto/`. Use case interfaces accept plain field parameters. `SagaCommandHandler` (thin adapter) routes DTOs to `ReceiptPdfDocumentService` (which implements use cases) by extracting fields — no command objects flow into domain or application layers. `ReceiptPdfGeneratedEvent` moves to `application/dto/event/` as it extends TraceEvent (notification DTO, not domain).

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, saga-commons library, Jackson

---

## File Changes Overview

```
CREATING:
  infrastructure/adapter/in/kafka/dto/ReceiptProcessCommand.java      (rename from KafkaReceiptProcessCommand)
  infrastructure/adapter/in/kafka/dto/ReceiptCompensateCommand.java    (rename from KafkaReceiptCompensateCommand)
  application/port/in/ProcessReceiptPdfUseCase.java                  (new interface, plain params)
  application/port/in/CompensateReceiptPdfUseCase.java               (new interface, plain params)

MOVING:
  SagaCommandHandler.java          application/service/ → infrastructure/adapter/in/kafka/
  ReceiptPdfGeneratedEvent.java    infrastructure/adapter/out/messaging/ → application/dto/event/

DELETING:
  infrastructure/adapter/in/kafka/KafkaReceiptProcessCommand.java
  infrastructure/adapter/in/kafka/KafkaReceiptCompensateCommand.java
  infrastructure/adapter/in/kafka/KafkaCommandMapper.java
  application/usecase/ProcessReceiptPdfUseCase.java
  application/usecase/CompensateReceiptPdfUseCase.java
  application/service/SagaCommandHandler.java (original location)

MODIFYING:
  ReceiptPdfDocumentService.java    (implements use cases, plain-field method signatures)
  SagaRouteConfig.java               (new DTO package, extract fields before calling use cases)
  SagaReplyPublisher.java            (inline ReceiptPdfReplyEvent factory as private inner class)
  EventPublisher.java                (update import for moved ReceiptPdfGeneratedEvent)
```

---

## Before You Start

- Build the service to confirm it compiles: `mvn clean compile -q`
- Run tests to confirm baseline: `mvn clean test -q`
- Work inside the service directory: `/home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service`

---

## Task 1: Create `dto/` directory and new `ReceiptProcessCommand`

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/dto/ReceiptProcessCommand.java`
- Source: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/KafkaReceiptProcessCommand.java`

- [ ] **Step 1: Create directory and write new file**

```bash
mkdir -p src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/dto
```

Create `ReceiptProcessCommand.java` — rename of `KafkaReceiptProcessCommand` with package changed:

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;

import java.time.Instant;
import java.util.UUID;

public class ReceiptProcessCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("signedXmlUrl")
    private final String signedXmlUrl;

    @JsonCreator
    public ReceiptProcessCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("documentNumber") String documentNumber,
            @JsonProperty("signedXmlUrl") String signedXmlUrl) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.signedXmlUrl = signedXmlUrl;
    }

    public ReceiptProcessCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                 String documentId, String documentNumber, String signedXmlUrl) {
        super(sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.signedXmlUrl = signedXmlUrl;
    }

    public String getDocumentId()     { return documentId; }
    public String getDocumentNumber() { return documentNumber; }
    public String getSignedXmlUrl()  { return signedXmlUrl; }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn compile -q 2>&1 | head -20`
Expected: No errors related to the new file

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/dto/ReceiptProcessCommand.java
git commit -m "refactor: rename KafkaReceiptProcessCommand to ReceiptProcessCommand in dto/ package"
```

---

## Task 2: Create `ReceiptCompensateCommand` in `dto/`

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/dto/ReceiptCompensateCommand.java`
- Source: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/KafkaReceiptCompensateCommand.java`

- [ ] **Step 1: Write the new file**

```java
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
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn compile -q 2>&1 | head -20`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/dto/ReceiptCompensateCommand.java
git commit -m "refactor: rename KafkaReceiptCompensateCommand to ReceiptCompensateCommand in dto/ package"
```

---

## Task 3: Create `ProcessReceiptPdfUseCase` in `application/port/in/`

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/application/port/in/ProcessReceiptPdfUseCase.java`
- Delete: `src/main/java/com/wpanther/receipt/pdf/application/usecase/ProcessReceiptPdfUseCase.java` (later in Task 9)

- [ ] **Step 1: Write the new interface**

```java
package com.wpanther.receipt.pdf.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Inbound port for receipt PDF generation.
 * Called by SagaCommandHandler with plain fields — no command objects.
 */
public interface ProcessReceiptPdfUseCase {

    void process(String documentId, String documentNumber, String signedXmlUrl,
                String sagaId, SagaStep sagaStep, String correlationId) throws ReceiptPdfGenerationException;

    class ReceiptPdfGenerationException extends Exception {
        public ReceiptPdfGenerationException(String message) { super(message); }
        public ReceiptPdfGenerationException(String message, Throwable cause) { super(message, cause); }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn compile -q 2>&1 | head -20`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/receipt/pdf/application/port/in/ProcessReceiptPdfUseCase.java
git commit -m "refactor: add ProcessReceiptPdfUseCase in application/port/in/ with plain parameter signatures"
```

---

## Task 4: Create `CompensateReceiptPdfUseCase` in `application/port/in/`

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/application/port/in/CompensateReceiptPdfUseCase.java`
- Delete: `src/main/java/com/wpanther/receipt/pdf/application/usecase/CompensateReceiptPdfUseCase.java` (later in Task 9)

- [ ] **Step 1: Write the new interface**

```java
package com.wpanther.receipt.pdf.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Inbound port for receipt PDF compensation.
 * Called by SagaCommandHandler with plain fields — no command objects.
 */
public interface CompensateReceiptPdfUseCase {

    void compensate(String documentId, String sagaId, SagaStep sagaStep, String correlationId);
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn compile -q 2>&1 | head -20`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/receipt/pdf/application/port/in/CompensateReceiptPdfUseCase.java
git commit -m "refactor: add CompensateReceiptPdfUseCase in application/port/in/ with plain parameter signatures"
```

---

## Task 5: Rewrite `SagaCommandHandler` in `infrastructure/adapter/in/kafka/`

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/SagaCommandHandler.java` (new location)
- Delete: `src/main/java/com/wpanther/receipt/pdf/application/service/SagaCommandHandler.java` (later in Task 9)
- Modify: `ReceiptPdfDocumentService.java` (Task 7)

This is the thin adapter — it only extracts fields from DTOs and calls use case methods with plain parameters. All orchestration logic lives in `ReceiptPdfDocumentService`.

- [ ] **Step 1: Write the new thin SagaCommandHandler**

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka;

import com.wpanther.receipt.pdf.application.port.in.CompensateReceiptPdfUseCase;
import com.wpanther.receipt.pdf.application.port.in.ProcessReceiptPdfUseCase;
import com.wpanther.receipt.pdf.application.service.ReceiptPdfDocumentService;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.CompensateReceiptPdfCommand;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.ReceiptProcessCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga command handler — thin driving adapter that receives Kafka messages and calls use cases.
 * No command objects flow into domain or application layers — only plain field parameters.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final ReceiptPdfDocumentService pdfDocumentService;

    /**
     * Handle a ReceiptProcessCommand from saga orchestrator.
     * Delegates to ReceiptPdfDocumentService.process() with plain fields.
     * Catches ReceiptPdfGenerationException after the saga reply has been committed to outbox,
     * returns normally so Camel commits the Kafka offset.
     */
    public void handleProcessCommand(ReceiptProcessCommand command) {
        log.info("Handling ReceiptProcessCommand for saga {} document {}",
                command.getSagaId(), command.getDocumentNumber());
        try {
            pdfDocumentService.process(
                    command.getDocumentId(),
                    command.getDocumentNumber(),
                    command.getSignedXmlUrl(),
                    command.getSagaId(),
                    command.getSagaStep(),
                    command.getCorrelationId()
            );
        } catch (ProcessReceiptPdfUseCase.ReceiptPdfGenerationException e) {
            log.error("Receipt PDF generation failed for saga {}: {}",
                    command.getSagaId(), e.toString());
        }
    }

    /**
     * Handle a ReceiptCompensateCommand from saga orchestrator.
     * Delegates to ReceiptPdfDocumentService.compensate() with plain fields.
     */
    public void handleCompensation(CompensateReceiptPdfCommand command) {
        log.info("Handling compensation for saga {} document {}",
                command.getSagaId(), command.getDocumentId());
        pdfDocumentService.compensate(
                command.getDocumentId(),
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOrchestrationFailure(String sagaId, SagaStep sagaStep, String correlationId, Throwable cause) {
        try {
            pdfDocumentService.publishGenerationFailure(
                    sagaId, sagaStep, correlationId,
                    "Message routed to DLQ after retry exhaustion: " + describeThrowable(cause));
        } catch (Exception e) {
            log.error("Cannot notify orchestrator of DLQ failure for saga {}", sagaId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishCompensationOrchestrationFailure(String sagaId, SagaStep sagaStep, String correlationId, Throwable cause) {
        try {
            pdfDocumentService.publishCompensationFailure(
                    sagaId, sagaStep, correlationId,
                    "Compensation DLQ after retry exhaustion: " + describeThrowable(cause));
        } catch (Exception e) {
            log.error("Cannot notify orchestrator of compensation DLQ failure for saga {}", sagaId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOrchestrationFailureForUnparsedMessage(String sagaId, SagaStep sagaStep, String correlationId, Throwable cause) {
        try {
            pdfDocumentService.publishGenerationFailure(
                    sagaId, sagaStep, correlationId,
                    "Message routed to DLQ after deserialization failure: " + describeThrowable(cause));
        } catch (Exception e) {
            log.error("Cannot notify orchestrator of DLQ deserialization failure for saga {} — orchestrator must timeout", sagaId, e);
        }
    }

    private String describeThrowable(Throwable t) {
        if (t == null) return "unknown error";
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
    }
}
```

Note: The new `SagaCommandHandler` references `CompensateReceiptPdfCommand` and `ReceiptProcessCommand` from the `dto/` package, but those file names include `Compensate` vs `ReceiptCompensate`. Fix the import to match the actual class name created in Task 2.

Actually: in Task 1 I named it `ReceiptProcessCommand` and Task 2 I named it `ReceiptCompensateCommand`. Update imports accordingly.

- [ ] **Step 2: Compile and fix any errors**

Run: `mvn compile -q 2>&1 | head -40`
Expected: Compile errors in `ReceiptPdfDocumentService` (method signatures don't match yet) — this is expected

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/SagaCommandHandler.java
git commit -m "refactor: move SagaCommandHandler to infrastructure/adapter/in/kafka/ as thin adapter"
```

---

## Task 6: Move `ReceiptPdfGeneratedEvent` to `application/dto/event/`

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/application/dto/event/ReceiptPdfGeneratedEvent.java`
- Modify: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/EventPublisher.java`
- Delete: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/ReceiptPdfGeneratedEvent.java` (original, after verifying)

- [ ] **Step 1: Create the new file**

Create directory: `application/dto/event/`

```java
package com.wpanther.receipt.pdf.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when receipt PDF generation is completed.
 * Consumed by notification-service.
 */
@Getter
public class ReceiptPdfGeneratedEvent extends TraceEvent {

    private static final String EVENT_TYPE = "pdf.generated.receipt";
    private static final String SOURCE = "receipt-pdf-generation-service";
    private static final String TRACE_TYPE = "PDF_GENERATED";

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("documentUrl")
    private final String documentUrl;

    @JsonProperty("fileSize")
    private final long fileSize;

    @JsonProperty("xmlEmbedded")
    private final boolean xmlEmbedded;

    public ReceiptPdfGeneratedEvent(
            String sagaId,
            String documentId,
            String documentNumber,
            String documentUrl,
            long fileSize,
            boolean xmlEmbedded,
            String correlationId
    ) {
        super(sagaId, correlationId, SOURCE, TRACE_TYPE, null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @JsonCreator
    public ReceiptPdfGeneratedEvent(
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
            @JsonProperty("documentUrl") String documentUrl,
            @JsonProperty("fileSize") long fileSize,
            @JsonProperty("xmlEmbedded") boolean xmlEmbedded
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
    }
}
```

- [ ] **Step 2: Update EventPublisher import**

Change the import in `EventPublisher.java` from:
```java
import com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging.ReceiptPdfGeneratedEvent;
```
to:
```java
import com.wpanther.receipt.pdf.application.dto.event.ReceiptPdfGeneratedEvent;
```

- [ ] **Step 3: Compile**

Run: `mvn compile -q 2>&1 | head -40`
Expected: Errors in `ReceiptPdfDocumentService` (will fix in Task 7)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/wpanther/receipt/pdf/application/dto/event/ReceiptPdfGeneratedEvent.java
git add src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/EventPublisher.java
git commit -m "refactor: move ReceiptPdfGeneratedEvent to application/dto/event/"
```

---

## Task 7: Rewrite `ReceiptPdfDocumentService` to implement use cases

**Files:**
- Modify: `src/main/java/com/wpanther/receipt/pdf/application/service/ReceiptPdfDocumentService.java`

This is the largest change — `ReceiptPdfDocumentService` now implements `ProcessReceiptPdfUseCase` and `CompensateReceiptPdfUseCase`. It contains ALL orchestration logic. Methods that previously accepted command objects now accept plain fields.

- [ ] **Step 1: Rewrite ReceiptPdfDocumentService.java**

```java
package com.wpanther.receipt.pdf.application.service;

import com.wpanther.receipt.pdf.application.dto.event.ReceiptPdfGeneratedEvent;
import com.wpanther.receipt.pdf.application.port.in.CompensateReceiptPdfUseCase;
import com.wpanther.receipt.pdf.application.port.in.ProcessReceiptPdfUseCase;
import com.wpanther.receipt.pdf.application.port.out.PdfEventPort;
import com.wpanther.receipt.pdf.application.port.out.PdfStoragePort;
import com.wpanther.receipt.pdf.application.port.out.SagaReplyPort;
import com.wpanther.receipt.pdf.application.port.out.SignedXmlFetchPort;
import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;
import com.wpanther.receipt.pdf.domain.repository.ReceiptPdfDocumentRepository;
import com.wpanther.receipt.pdf.domain.service.ReceiptPdfGenerationService;
import com.wpanther.receipt.pdf.infrastructure.metrics.PdfGenerationMetrics;
import com.wpanther.saga.domain.enums.SagaStep;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptPdfDocumentService implements ProcessReceiptPdfUseCase, CompensateReceiptPdfUseCase {

    private final ReceiptPdfDocumentRepository repository;
    private final PdfEventPort pdfEventPort;
    private final SagaReplyPort sagaReplyPort;
    private final PdfStoragePort pdfStoragePort;
    private final SignedXmlFetchPort signedXmlFetchPort;
    private final ReceiptPdfGenerationService pdfGenerationService;
    private final PdfGenerationMetrics pdfGenerationMetrics;
    private final int maxRetries;

    @Override
    public void process(String documentId, String documentNumber, String signedXmlUrl,
                       String sagaId, SagaStep sagaStep, String correlationId) throws ReceiptPdfGenerationException {
        MDC.put("sagaId", sagaId);
        MDC.put("correlationId", correlationId);
        MDC.put("documentNumber", documentNumber);
        MDC.put("documentId", documentId);
        try {
            log.info("Processing receipt PDF for saga {} document {}", sagaId, documentNumber);
            if (signedXmlUrl == null || signedXmlUrl.isBlank()) {
                publishGenerationFailure(sagaId, sagaStep, correlationId, "signedXmlUrl is null or blank");
                return;
            }
            if (documentId == null || documentId.isBlank()) {
                publishGenerationFailure(sagaId, sagaStep, correlationId, "documentId is null or blank");
                return;
            }
            if (documentNumber == null || documentNumber.isBlank()) {
                publishGenerationFailure(sagaId, sagaStep, correlationId, "documentNumber is null or blank");
                return;
            }

            Optional<ReceiptPdfDocument> existing = findByReceiptId(documentId);

            if (existing.isPresent() && existing.get().isCompleted()) {
                publishIdempotentSuccess(existing.get(), documentId, documentNumber, sagaId, sagaStep, correlationId);
                return;
            }

            int previousRetryCount = existing.map(ReceiptPdfDocument::getRetryCount).orElse(-1);

            if (existing.isPresent()) {
                if (existing.get().isMaxRetriesExceeded(maxRetries)) {
                    publishRetryExhausted(sagaId, sagaStep, correlationId, documentId, documentNumber);
                    return;
                }
            }

            ReceiptPdfDocument document;
            if (existing.isPresent()) {
                document = replaceAndBeginGeneration(existing.get().getId(), previousRetryCount, documentId, documentNumber);
            } else {
                document = beginGeneration(documentId, documentNumber);
            }

            String s3Key = null;
            try {
                String xml = signedXmlFetchPort.fetch(signedXmlUrl);
                byte[] pdfBytes = pdfGenerationService.generatePdf(documentNumber, xml);
                s3Key = pdfStoragePort.store(documentNumber, pdfBytes);
                String fileUrl = pdfStoragePort.resolveUrl(s3Key);

                completeGenerationAndPublish(
                        document.getId(), s3Key, fileUrl, pdfBytes.length, previousRetryCount,
                        documentId, documentNumber, sagaId, sagaStep, correlationId);

            } catch (CallNotPermittedException e) {
                log.warn("Circuit breaker OPEN for saga {} document {}: {}", sagaId, documentNumber, e.getMessage());
                failGenerationAndPublish(
                        document.getId(), "Circuit breaker open: " + e.getMessage(),
                        previousRetryCount, documentId, sagaId, sagaStep, correlationId);

            } catch (RestClientException e) {
                log.warn("HTTP error fetching signed XML for saga {} document {}: {}", sagaId, documentNumber, e.getMessage());
                failGenerationAndPublish(
                        document.getId(), "HTTP error fetching signed XML: " + describeThrowable(e),
                        previousRetryCount, documentId, sagaId, sagaStep, correlationId);

            } catch (Exception e) {
                if (s3Key != null) {
                    try { pdfStoragePort.delete(s3Key); }
                    catch (Exception del) {
                        log.error("[ORPHAN_PDF] s3Key={} saga={} error={}", s3Key, sagaId, describeThrowable(del));
                    }
                }
                log.error("PDF generation failed for saga {} document {}: {}", sagaId, documentNumber, e.getMessage(), e);
                failGenerationAndPublish(
                        document.getId(), describeThrowable(e), previousRetryCount, documentId, sagaId, sagaStep, correlationId);
            }

        } catch (OptimisticLockingFailureException e) {
            log.warn("Concurrent modification for saga {}: {}", sagaId, e.getMessage());
            publishGenerationFailure(sagaId, sagaStep, correlationId, "Concurrent modification: " + e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void compensate(String documentId, String sagaId, SagaStep sagaStep, String correlationId) {
        MDC.put("sagaId", sagaId);
        MDC.put("correlationId", correlationId);
        MDC.put("documentId", documentId);
        try {
            log.info("Handling compensation for saga {} document {}", sagaId, documentId);
            Optional<ReceiptPdfDocument> existing = findByReceiptId(documentId);

            if (existing.isPresent()) {
                ReceiptPdfDocument doc = existing.get();
                deleteById(doc.getId());
                if (doc.getDocumentPath() != null) {
                    try { pdfStoragePort.delete(doc.getDocumentPath()); }
                    catch (Exception e) {
                        log.warn("Failed to delete PDF from MinIO for saga {} key {}: {}", sagaId, doc.getDocumentPath(), e.getMessage());
                    }
                }
                log.info("Compensated ReceiptPdfDocument {} for saga {}", doc.getId(), sagaId);
            } else {
                log.info("No document for documentId {} — already compensated", documentId);
            }
            publishCompensated(sagaId, sagaStep, correlationId);

        } catch (Exception e) {
            log.error("Failed to compensate for saga {}: {}", sagaId, e.getMessage(), e);
            publishCompensationFailure(sagaId, sagaStep, correlationId, "Compensation failed: " + describeThrowable(e));
        } finally {
            MDC.clear();
        }
    }

    @Transactional(readOnly = true)
    public Optional<ReceiptPdfDocument> findByReceiptId(String receiptId) {
        return repository.findByReceiptId(receiptId);
    }

    @Transactional
    public ReceiptPdfDocument beginGeneration(String receiptId, String receiptNumber) {
        log.info("Initiating PDF generation for receipt: {}", receiptNumber);
        ReceiptPdfDocument doc = ReceiptPdfDocument.builder()
                .receiptId(receiptId)
                .receiptNumber(receiptNumber)
                .build();
        doc.startGeneration();
        return repository.save(doc);
    }

    @Transactional
    public ReceiptPdfDocument replaceAndBeginGeneration(
            UUID existingId, int previousRetryCount, String receiptId, String receiptNumber) {
        log.info("Replacing document {} and re-starting generation for receipt: {}", existingId, receiptNumber);
        repository.deleteById(existingId);
        repository.flush();
        ReceiptPdfDocument doc = ReceiptPdfDocument.builder()
                .receiptId(receiptId)
                .receiptNumber(receiptNumber)
                .build();
        doc.startGeneration();
        doc.incrementRetryCountTo(previousRetryCount + 1);
        return repository.save(doc);
    }

    @Transactional
    public void completeGenerationAndPublish(UUID documentId, String s3Key, String fileUrl,
                                             long fileSize, int previousRetryCount,
                                             String documentId, String documentNumber,
                                             String sagaId, SagaStep sagaStep, String correlationId) {
        ReceiptPdfDocument doc = requireDocument(documentId);
        doc.markCompleted(s3Key, fileUrl, fileSize);
        doc.markXmlEmbedded();
        applyRetryCount(doc, previousRetryCount);
        doc = repository.save(doc);

        pdfEventPort.publishGenerated(buildGeneratedEvent(doc, documentId, documentNumber, sagaId, correlationId));
        sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId, doc.getDocumentUrl(), doc.getFileSize());

        log.info("Completed PDF generation for saga {} receipt {}", sagaId, doc.getReceiptNumber());
    }

    @Transactional
    public void failGenerationAndPublish(UUID documentId, String errorMessage,
                                         int previousRetryCount,
                                         String documentId, String sagaId, SagaStep sagaStep, String correlationId) {
        String safeError = errorMessage != null ? errorMessage : "PDF generation failed";
        ReceiptPdfDocument doc = requireDocument(documentId);
        doc.markFailed(safeError);
        applyRetryCount(doc, previousRetryCount);
        repository.save(doc);

        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, safeError);

        log.warn("PDF generation failed for saga {} receipt {}: {}", sagaId, doc.getReceiptNumber(), safeError);
    }

    @Transactional
    public void deleteById(UUID documentId) {
        repository.deleteById(documentId);
        repository.flush();
    }

    @Transactional
    public void publishIdempotentSuccess(ReceiptPdfDocument existing,
                                        String documentId, String documentNumber,
                                        String sagaId, SagaStep sagaStep, String correlationId) {
        pdfEventPort.publishGenerated(buildGeneratedEvent(existing, documentId, documentNumber, sagaId, correlationId));
        sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId, existing.getDocumentUrl(), existing.getFileSize());
        log.warn("Receipt PDF already generated for saga {} — re-publishing SUCCESS reply", sagaId);
    }

    @Transactional
    public void publishRetryExhausted(String sagaId, SagaStep sagaStep, String correlationId,
                                      String documentId, String documentNumber) {
        pdfGenerationMetrics.recordRetryExhausted(sagaId, documentId, documentNumber);
        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, "Maximum retry attempts exceeded");
        log.error("Max retries exceeded for saga {} document {}", sagaId, documentNumber);
    }

    @Transactional
    public void publishGenerationFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, errorMessage);
    }

    @Transactional
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {
        sagaReplyPort.publishCompensated(sagaId, sagaStep, correlationId);
    }

    @Transactional
    public void publishCompensationFailure(String sagaId, SagaStep sagaStep, String correlationId, String error) {
        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, error);
    }

    private ReceiptPdfGeneratedEvent buildGeneratedEvent(ReceiptPdfDocument doc,
                                                         String documentId, String documentNumber,
                                                         String sagaId, String correlationId) {
        return new ReceiptPdfGeneratedEvent(
                sagaId, documentId, doc.getReceiptNumber(),
                doc.getDocumentUrl(), doc.getFileSize(),
                doc.isXmlEmbedded(), correlationId);
    }

    private ReceiptPdfDocument requireDocument(UUID documentId) {
        return repository.findById(documentId)
                .orElseThrow(() -> {
                    log.error("ReceiptPdfDocument not found for id={}", documentId);
                    return new IllegalStateException("Expected receipt PDF document is absent");
                });
    }

    private void applyRetryCount(ReceiptPdfDocument doc, int previousRetryCount) {
        if (previousRetryCount < 0) return;
        doc.incrementRetryCountTo(previousRetryCount + 1);
    }

    private String describeThrowable(Throwable t) {
        if (t == null) return "unknown error";
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
    }
}
```

Note: There is a naming conflict in `completeGenerationAndPublish` and `failGenerationAndPublish` — the parameter `documentId` shadows the method parameter `documentId`. Fix by renaming one to `cmdDocumentId`:

```java
    @Transactional
    public void completeGenerationAndPublish(UUID documentId, String s3Key, String fileUrl,
                                             long fileSize, int previousRetryCount,
                                             String cmdDocumentId, String cmdDocumentNumber,
                                             String sagaId, SagaStep sagaStep, String correlationId) {
```

And update the call accordingly.

Also: `ReceiptPdfDocumentService` now needs to accept `SignedXmlFetchPort` and `ReceiptPdfGenerationService` in its constructor. The current constructor only has 4 parameters. Add them.

- [ ] **Step 2: Compile**

Run: `mvn compile -q 2>&1 | head -40`
Expected: Should compile now (or very few errors)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/receipt/pdf/application/service/ReceiptPdfDocumentService.java
git commit -m "refactor: rewrite ReceiptPdfDocumentService to implement use cases with plain field signatures"
```

---

## Task 8: Inline `ReceiptPdfReplyEvent` factory in `SagaReplyPublisher`

**Files:**
- Modify: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/SagaReplyPublisher.java`
- Delete: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/ReceiptPdfReplyEvent.java` (later in Task 9)

`ReceiptPdfReplyEvent` is replaced with a private static inner class in `SagaReplyPublisher`. The factory methods (`success()`, `failure()`, `compensated()`) are preserved as static methods on the inner class.

- [ ] **Step 1: Rewrite SagaReplyPublisher to inline the reply event factory**

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.receipt.pdf.application.port.out.SagaReplyPort;
import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaReply;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaReplyPublisher implements SagaReplyPort {

    private static final String REPLY_TOPIC = "saga.reply.receipt-pdf";
    private static final String AGGREGATE_TYPE = OutboxConstants.AGGREGATE_TYPE;

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId,
                               String pdfUrl, long pdfSize) {
        ReceiptPdfReplyEvent reply = ReceiptPdfReplyEvent.success(sagaId, sagaStep, correlationId, pdfUrl, pdfSize);

        Map<String, String> headers = Map.of(
                "sagaId", sagaId,
                "correlationId", correlationId,
                "status", "SUCCESS"
        );

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                REPLY_TOPIC,
                sagaId,
                toJson(headers)
        );

        log.info("Published SUCCESS saga reply for saga {} step {}", sagaId, sagaStep);
        log.debug("SUCCESS reply pdfUrl={} pdfSize={}", pdfUrl, pdfSize);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        ReceiptPdfReplyEvent reply = ReceiptPdfReplyEvent.failure(sagaId, sagaStep, correlationId, errorMessage);

        Map<String, String> headers = Map.of(
                "sagaId", sagaId,
                "correlationId", correlationId,
                "status", "FAILURE"
        );

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                REPLY_TOPIC,
                sagaId,
                toJson(headers)
        );

        log.info("Published FAILURE saga reply for saga {} step {}: {}", sagaId, sagaStep, errorMessage);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {
        ReceiptPdfReplyEvent reply = ReceiptPdfReplyEvent.compensated(sagaId, sagaStep, correlationId);

        Map<String, String> headers = Map.of(
                "sagaId", sagaId,
                "correlationId", correlationId,
                "status", "COMPENSATED"
        );

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                REPLY_TOPIC,
                sagaId,
                toJson(headers)
        );

        log.info("Published COMPENSATED saga reply for saga {} step {}", sagaId, sagaStep);
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event headers", e);
        }
    }

    // Inline factory — replaces infrastructure/adapter/out/messaging/ReceiptPdfReplyEvent
    private static class ReceiptPdfReplyEvent extends SagaReply {
        private static final long serialVersionUID = 1L;
        private final String pdfUrl;
        private final Long pdfSize;

        private ReceiptPdfReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, ReplyStatus status,
                                     String pdfUrl, Long pdfSize) {
            super(sagaId, sagaStep, correlationId, status);
            this.pdfUrl = pdfUrl;
            this.pdfSize = pdfSize;
        }

        private ReceiptPdfReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, ReplyStatus status) {
            super(sagaId, sagaStep, correlationId, status);
            this.pdfUrl = null;
            this.pdfSize = null;
        }

        private ReceiptPdfReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
            super(sagaId, sagaStep, correlationId, errorMessage);
            this.pdfUrl = null;
            this.pdfSize = null;
        }

        public static ReceiptPdfReplyEvent success(String sagaId, SagaStep sagaStep, String correlationId,
                                                    String pdfUrl, Long pdfSize) {
            return new ReceiptPdfReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS, pdfUrl, pdfSize);
        }

        public static ReceiptPdfReplyEvent failure(String sagaId, SagaStep sagaStep, String correlationId,
                                                   String errorMessage) {
            return new ReceiptPdfReplyEvent(sagaId, sagaStep, correlationId, errorMessage);
        }

        public static ReceiptPdfReplyEvent compensated(String sagaId, SagaStep sagaStep, String correlationId) {
            return new ReceiptPdfReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED);
        }

        public String getPdfUrl() { return pdfUrl; }
        public Long getPdfSize() { return pdfSize; }
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile -q 2>&1 | head -40`
Expected: Should compile cleanly

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/SagaReplyPublisher.java
git commit -m "refactor: inline ReceiptPdfReplyEvent factory in SagaReplyPublisher"
```

---

## Task 9: Delete old files

**Files:**
- Delete: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/KafkaReceiptProcessCommand.java`
- Delete: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/KafkaReceiptCompensateCommand.java`
- Delete: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/KafkaCommandMapper.java`
- Delete: `src/main/java/com/wpanther/receipt/pdf/application/usecase/ProcessReceiptPdfUseCase.java`
- Delete: `src/main/java/com/wpanther/receipt/pdf/application/usecase/CompensateReceiptPdfUseCase.java`
- Delete: `src/main/java/com/wpanther/receipt/pdf/application/service/SagaCommandHandler.java` (original location)
- Delete: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/ReceiptPdfReplyEvent.java`

- [ ] **Step 1: Delete all old files**

```bash
rm src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/KafkaReceiptProcessCommand.java
rm src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/KafkaReceiptCompensateCommand.java
rm src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/KafkaCommandMapper.java
rm src/main/java/com/wpanther/receipt/pdf/application/usecase/ProcessReceiptPdfUseCase.java
rm src/main/java/com/wpanther/receipt/pdf/application/usecase/CompensateReceiptPdfUseCase.java
rm src/main/java/com/wpanther/receipt/pdf/application/service/SagaCommandHandler.java
rm src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/ReceiptPdfReplyEvent.java
```

- [ ] **Step 2: Compile**

Run: `mvn compile -q 2>&1 | head -40`
Expected: Clean compile

- [ ] **Step 3: Commit**

```bash
git add -A  # stage deletions
git commit -m "refactor: delete old saga command classes and Kafka prefix files"
```

---

## Task 10: Update `SagaRouteConfig` — new DTO package, plain field calls

**Files:**
- Modify: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/SagaRouteConfig.java`

Key changes:
1. Change `KafkaReceiptProcessCommand` → `ReceiptProcessCommand` (from `dto/` package)
2. Change `KafkaReceiptCompensateCommand` → `ReceiptCompensateCommand` (from `dto/` package)
3. Route processors call `sagaCommandHandler.handleProcessCommand(cmd)` and `sagaCommandHandler.handleCompensation(cmd)` with the DTO — the handler extracts fields
4. `onPrepareFailure` instanceof checks updated to new DTO names
5. `publishOrchestrationFailure(cmd, cause)` calls updated to `publishOrchestrationFailure(cmd.getSagaId(), cmd.getSagaStep(), cmd.getCorrelationId(), cause)` with plain fields

- [ ] **Step 1: Rewrite SagaRouteConfig**

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.CompensateReceiptPdfCommand;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.ReceiptProcessCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaRouteConfig extends RouteBuilder {

    private final SagaCommandHandler sagaCommandHandler;
    private final ObjectMapper objectMapper;

    @Override
    public void configure() throws Exception {

        errorHandler(deadLetterChannel(
                        "kafka:{{app.kafka.topics.dlq}}?brokers={{app.kafka.bootstrap-servers}}")
                        .maximumRedeliveries(3)
                        .redeliveryDelay(1000)
                        .useExponentialBackOff()
                        .backOffMultiplier(2)
                        .maximumRedeliveryDelay(10000)
                        .logExhausted(true)
                        .logStackTrace(true)
                        .onPrepareFailure(exchange -> {
                            Throwable cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                            Object body = exchange.getIn().getBody();
                            if (body instanceof ReceiptProcessCommand cmd) {
                                log.error("DLQ: notifying orchestrator of retry exhaustion for saga {} document {}",
                                        cmd.getSagaId(), cmd.getDocumentNumber());
                                sagaCommandHandler.publishOrchestrationFailure(
                                        cmd.getSagaId(), cmd.getSagaStep(), cmd.getCorrelationId(), cause);
                            } else if (body instanceof CompensateReceiptPdfCommand cmd) {
                                log.error("DLQ: notifying orchestrator of compensation retry exhaustion for saga {} document {}",
                                        cmd.getSagaId(), cmd.getDocumentId());
                                sagaCommandHandler.publishCompensationOrchestrationFailure(
                                        cmd.getSagaId(), cmd.getSagaStep(), cmd.getCorrelationId(), cause);
                            } else {
                                log.error("DLQ: body not deserialized ({}); attempting saga metadata recovery",
                                        body == null ? "null" : body.getClass().getSimpleName());
                                recoverAndNotifyOrchestrator(body, cause);
                            }
                        }));

        from("kafka:{{app.kafka.topics.saga-command-receipt-pdf}}"
                        + "?brokers={{app.kafka.bootstrap-servers}}"
                        + "&groupId={{app.kafka.consumer.command-group-id}}"
                        + "&autoOffsetReset=earliest"
                        + "&autoCommitEnable=false"
                        + "&breakOnFirstError={{app.kafka.consumer.break-on-first-error:true}}"
                        + "&maxPollRecords={{app.kafka.consumer.max-poll-records:100}}"
                        + "&consumersCount={{app.kafka.consumer.consumers-count:3}}")
                .routeId("saga-command-consumer")
                .log(LoggingLevel.DEBUG, "Received saga command from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, ReceiptProcessCommand.class)
                .process(exchange -> {
                        ReceiptProcessCommand cmd = exchange.getIn().getBody(ReceiptProcessCommand.class);
                        log.info("Processing saga command for saga: {}, document: {}",
                                        cmd.getSagaId(), cmd.getDocumentNumber());
                        sagaCommandHandler.handleProcessCommand(cmd);
                })
                .log("Successfully processed saga command");

        from("kafka:{{app.kafka.topics.saga-compensation-receipt-pdf}}"
                        + "?brokers={{app.kafka.bootstrap-servers}}"
                        + "&groupId={{app.kafka.consumer.compensation-group-id}}"
                        + "&autoOffsetReset=earliest"
                        + "&autoCommitEnable=false"
                        + "&breakOnFirstError={{app.kafka.consumer.break-on-first-error:true}}"
                        + "&maxPollRecords={{app.kafka.consumer.max-poll-records:100}}"
                        + "&consumersCount={{app.kafka.consumer.consumers-count:3}}")
                .routeId("saga-compensation-consumer")
                .log(LoggingLevel.DEBUG, "Received compensation command from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, CompensateReceiptPdfCommand.class)
                .process(exchange -> {
                        CompensateReceiptPdfCommand cmd = exchange.getIn().getBody(CompensateReceiptPdfCommand.class);
                        log.info("Processing compensation for saga: {}, document: {}",
                                        cmd.getSagaId(), cmd.getDocumentId());
                        sagaCommandHandler.handleCompensation(cmd);
                })
                .log("Successfully processed compensation command");
    }

    private void recoverAndNotifyOrchestrator(Object body, Throwable cause) {
        if (body == null) {
            log.error("DLQ: null message body — orchestrator must timeout");
            return;
        }
        try {
            byte[] rawBytes = body instanceof byte[] b
                    ? b
                    : body.toString().getBytes(StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(rawBytes);
            String sagaId = node.path("sagaId").asText(null);
            String sagaStepStr = node.path("sagaStep").asText(null);
            String correlationId = node.path("correlationId").asText(null);

            if (sagaId == null || sagaStepStr == null) {
                log.error("DLQ: saga metadata missing in raw message — orchestrator must timeout");
                return;
            }
            SagaStep sagaStep = objectMapper.readValue("\"" + sagaStepStr + "\"", SagaStep.class);
            sagaCommandHandler.publishOrchestrationFailureForUnparsedMessage(sagaId, sagaStep, correlationId, cause);
        } catch (Exception parseEx) {
            log.error("DLQ: cannot parse raw message for saga metadata — orchestrator must timeout", parseEx);
        }
    }
}
```

Note: The `CompensateReceiptPdfCommand` class name in Task 2 was `ReceiptCompensateCommand`. Fix accordingly.

- [ ] **Step 2: Compile**

Run: `mvn compile -q 2>&1 | head -40`
Expected: Should compile cleanly

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/SagaRouteConfig.java
git commit -m "refactor: update SagaRouteConfig to use new DTO package"
```

---

## Task 11: Update tests

**Files:**
- Modify: `src/test/java/com/wpanther/receipt/pdf/application/service/SagaCommandHandlerTest.java`
- Modify: `src/test/java/com/wpanther/receipt/pdf/application/service/ReceiptPdfDocumentServiceTest.java`
- Modify: `src/test/java/com/wpanther/receipt/pdf/infrastructure/config/CamelRouteConfigTest.java`
- Delete: `src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/KafkaCommandMapperTest.java`

### Update SagaCommandHandlerTest

The test now tests the thin adapter. It should mock `ReceiptPdfDocumentService` and verify `handleProcessCommand`/`handleCompensation` delegate correctly with plain field calls.

```java
package com.wpanther.receipt.pdf.application.service;

import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.receipt.pdf.domain.model.GenerationStatus;
import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.SagaCommandHandler;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.CompensateReceiptPdfCommand;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.ReceiptProcessCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaCommandHandler Unit Tests")
class SagaCommandHandlerTest {

    @Mock
    private ReceiptPdfDocumentService pdfDocumentService;

    private SagaCommandHandler getHandler() {
        return new SagaCommandHandler(pdfDocumentService);
    }

    private ReceiptProcessCommand createProcessCommand() {
        return new ReceiptProcessCommand(
                "saga-001", SagaStep.GENERATE_RECEIPT_PDF, "corr-456",
                "doc-123", "RCP-2024-001",
                "http://minio:9000/signed/receipt-signed.xml"
        );
    }

    private CompensateReceiptPdfCommand createCompensateCommand() {
        return new CompensateReceiptPdfCommand(
                "saga-001", SagaStep.GENERATE_RECEIPT_PDF, "corr-456",
                "doc-123"
        );
    }

    @Test
    @DisplayName("handleProcessCommand() delegates to pdfDocumentService.process() with plain fields")
    void testHandleProcessCommand_DelegatesToService() throws Exception {
        // Given
        ReceiptProcessCommand command = createProcessCommand();

        // When
        getHandler().handleProcessCommand(command);

        // Then
        verify(pdfDocumentService).process(
                eq("doc-123"),
                eq("RCP-2024-001"),
                eq("http://minio:9000/signed/receipt-signed.xml"),
                eq("saga-001"),
                eq(SagaStep.GENERATE_RECEIPT_PDF),
                eq("corr-456")
        );
    }

    @Test
    @DisplayName("handleCompensation() delegates to pdfDocumentService.compensate() with plain fields")
    void testHandleCompensation_DelegatesToService() {
        // Given
        CompensateReceiptPdfCommand command = createCompensateCommand();

        // When
        getHandler().handleCompensation(command);

        // Then
        verify(pdfDocumentService).compensate(
                eq("doc-123"),
                eq("saga-001"),
                eq(SagaStep.GENERATE_RECEIPT_PDF),
                eq("corr-456")
        );
    }

    @Test
    @DisplayName("publishOrchestrationFailure() calls pdfDocumentService.publishGenerationFailure() with plain fields")
    void testPublishOrchestrationFailure() {
        // Given
        Throwable cause = new RuntimeException("DLQ error");

        // When
        getHandler().publishOrchestrationFailure("saga-001", SagaStep.GENERATE_RECEIPT_PDF, "corr-456", cause);

        // Then
        verify(pdfDocumentService).publishGenerationFailure(
                eq("saga-001"),
                eq(SagaStep.GENERATE_RECEIPT_PDF),
                eq("corr-456"),
                contains("Message routed to DLQ")
        );
    }
}
```

### Update ReceiptPdfDocumentServiceTest

Update all test methods to use plain field parameters instead of command objects.

Changes for each test:
- `testPublishIdempotentSuccess`: change `publishIdempotentSuccess(doc, command)` → `publishIdempotentSuccess(doc, "saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1")`
- `testPublishRetryExhausted`: change `publishRetryExhausted(command)` → `publishRetryExhausted("saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1", "doc-1", "RCP-001")`
- `testPublishGenerationFailure`: change `publishGenerationFailure(command, errorMessage)` → `publishGenerationFailure("saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1", errorMessage)`
- `testPublishCompensated`: change `publishCompensated(command)` → `publishCompensated("saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1")`
- `testPublishCompensationFailure`: change `publishCompensationFailure(command, error)` → `publishCompensationFailure("saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr-1", error)`
- `testCompleteGenerationAndPublish`: update call to use plain fields
- `testFailGenerationAndPublish`: update call to use plain fields

Also remove all imports for `KafkaReceiptProcessCommand` and `KafkaReceiptCompensateCommand`.

### Update CamelRouteConfigTest

Update all references from `KafkaReceiptProcessCommand` → `ReceiptProcessCommand` and `KafkaReceiptCompensateCommand` → `CompensateReceiptPdfCommand`.

Also update the `SagaCommandHandler` import to the new location and remove imports for old `usecase/` interfaces (since `SagaRouteConfig` no longer depends on use case interfaces directly — it only depends on `SagaCommandHandler`).

### Delete KafkaCommandMapperTest

```bash
rm src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/KafkaCommandMapperTest.java
```

- [ ] **Step 1: Update SagaCommandHandlerTest**

- [ ] **Step 2: Update ReceiptPdfDocumentServiceTest**

- [ ] **Step 3: Update CamelRouteConfigTest**

- [ ] **Step 4: Delete KafkaCommandMapperTest**

- [ ] **Step 5: Run tests**

Run: `mvn clean test 2>&1 | tail -30`
Expected: BUILD SUCCESS (all tests pass)

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: update tests for layer separation refactor"
```

---

## Task 12: Build and test

- [ ] **Step 1: Full clean compile**

Run: `mvn clean compile 2>&1 | tail -20`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run tests**

Run: `mvn clean test 2>&1 | tail -30`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit all remaining changes**

```bash
git add -A
git commit -m "refactor: complete layer separation — saga types in infrastructure, plain-parameter use cases in application/port/in/"
```

---

## Verification Checklist

After all tasks:

```bash
# 1. Compile check
mvn clean compile -q

# 2. All tests pass
mvn clean test -q

# 3. Confirm no Kafka-prefixed command files remain
ls src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/
# Expected: SagaCommandHandler.java, SagaRouteConfig.java (NO KafkaReceipt*Command, NO KafkaCommandMapper)

# 4. Confirm new structure
ls src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/dto/
# Expected: CompensateReceiptPdfCommand.java, ReceiptProcessCommand.java

ls src/main/java/com/wpanther/receipt/pdf/application/port/in/
# Expected: CompensateReceiptPdfUseCase.java, ProcessReceiptPdfUseCase.java

ls src/main/java/com/wpanther/receipt/pdf/application/dto/event/
# Expected: ReceiptPdfGeneratedEvent.java

ls src/main/java/com/wpanther/receipt/pdf/application/usecase/
# Expected: empty (or directory removed)

ls src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/
# Expected: EventPublisher.java, OutboxConstants.java, SagaReplyPublisher.java (NO ReceiptPdfReplyEvent)
```

---

## Self-Review Checklist

- [ ] `ReceiptProcessCommand` and `ReceiptCompensateCommand` are in `infrastructure/adapter/in/kafka/dto/`
- [ ] Use case interfaces in `application/port/in/` have plain parameter signatures (no command objects)
- [ ] `SagaCommandHandler` is thin adapter in `infrastructure/adapter/in/kafka/` — only routes DTOs to service
- [ ] `ReceiptPdfDocumentService` implements use cases and contains all orchestration logic
- [ ] `ReceiptPdfGeneratedEvent` is in `application/dto/event/`
- [ ] `ReceiptPdfReplyEvent` is inlined in `SagaReplyPublisher`
- [ ] `KafkaCommandMapper` is deleted
- [ ] `application/usecase/` is empty (deleted or directory removed)
- [ ] All tests pass with `mvn clean test`
- [ ] Compilation succeeds with `mvn clean compile`
