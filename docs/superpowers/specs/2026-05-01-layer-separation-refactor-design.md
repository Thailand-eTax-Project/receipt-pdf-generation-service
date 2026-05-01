# receipt-pdf-generation-service Layer Separation Refactoring

**Date:** 2026-05-01
**Author:** Claude Code
**Status:** Draft

## Context

`receipt-pdf-generation-service` needs to align with the hexagonal/port-adapters architecture pattern established in `taxinvoice-processing-service` (the reference implementation). The goal is ensuring saga infrastructure types (Kafka DTOs, Jackson annotations) do not leak into the application or domain layers.

## Problem Statement

Three architectural violations exist:

1. **Use case interfaces accept command objects** — `ProcessReceiptPdfUseCase.handle(KafkaReceiptProcessCommand)` and `CompensateReceiptPdfUseCase.handle(KafkaReceiptCompensateCommand)` pass infrastructure objects into the application layer. Use case interfaces should only accept plain field parameters.

2. **SagaCommandHandler lives in `application/service/` and contains all orchestration logic** — it should be a thin driving adapter in `infrastructure/adapter/in/kafka/`. All business orchestration should live in `ReceiptPdfDocumentService` (which implements the use cases), matching how `TaxInvoiceProcessingService` implements `ProcessTaxInvoiceUseCase` in the reference.

3. **`publishOrchestrationFailure` / `publishCompensationOrchestrationFailure` on SagaCommandHandler accept command objects** — these need plain field signatures so the application layer remains decoupled from infrastructure types.

## Reference Architecture (taxinvoice-processing-service)

In the reference:
- `SagaCommandHandler` (`infrastructure/adapter/in/messaging/`) is a thin adapter (~88 lines) that calls `processTaxInvoiceUseCase.process(...)` and `compensateTaxInvoiceUseCase.compensate(...)` with plain fields
- `TaxInvoiceProcessingService` (`application/service/`) implements both use case interfaces and contains ALL business logic: parsing, idempotency, persistence, event publishing, saga reply publishing, compensation
- `SagaCommandHandler.handleProcessCommand(ProcessTaxInvoiceCommand)` — extracts fields, calls `process()`
- `SagaCommandHandler.handleCompensation(CompensateTaxInvoiceCommand)` — extracts fields, calls `compensate()`
- The use case methods (`process()`/`compensate()`) throw typed exceptions after the outbox reply is committed, allowing the handler to return normally so Camel commits the Kafka offset

## Target Architecture

```
infrastructure/adapter/in/kafka/
├── dto/
│   ├── ProcessReceiptPdfCommand       ← renamed from KafkaReceiptProcessCommand
│   └── CompensateReceiptPdfCommand     ← renamed from KafkaReceiptCompensateCommand
├── SagaCommandHandler                  ← thin adapter: only routes DTOs to use cases
└── SagaRouteConfig                      ← (unchanged)

application/port/in/
├── ProcessReceiptPdfUseCase             ← plain field parameters
└── CompensateReceiptPdfUseCase          ← plain field parameters

application/service/
└── ReceiptPdfDocumentService           ← IMPLEMENTS use case interfaces; contains all orchestration logic

application/dto/event/
└── ReceiptPdfGeneratedEvent            ← (moved from infrastructure/adapter/out/messaging/)

infrastructure/adapter/out/messaging/
├── SagaReplyPublisher                  ← (ReceiptPdfReplyEvent inlined here)
└── EventPublisher                      ← (updated import for moved ReceiptPdfGeneratedEvent)

domain/
└── ...                                 ← (unchanged — no saga types)
```

**Key rule:** No command objects flow into application or domain layers. `SagaCommandHandler` extracts fields from DTOs and passes them as plain parameters. All business orchestration lives in `ReceiptPdfDocumentService`.

## Design

### 1. Rename Kafka DTOs to remove `Kafka` prefix

Move `infrastructure/adapter/in/kafka/KafkaReceiptProcessCommand` → `dto/ProcessReceiptPdfCommand`
Move `infrastructure/adapter/in/kafka/KafkaReceiptCompensateCommand` → `dto/CompensateReceiptPdfCommand`

Package: `com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto`

### 2. New use case interfaces in `application/port/in/`

**ProcessReceiptPdfUseCase** — plain field parameters, throws `ReceiptPdfGenerationException`:

```java
package com.wpanther.receipt.pdf.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

public interface ProcessReceiptPdfUseCase {
    void process(String documentId, String documentNumber, String signedXmlUrl,
                 String sagaId, SagaStep sagaStep, String correlationId) throws ReceiptPdfGenerationException;

    class ReceiptPdfGenerationException extends Exception {
        public ReceiptPdfGenerationException(String message) { super(message); }
        public ReceiptPdfGenerationException(String message, Throwable cause) { super(message, cause); }
    }
}
```

**CompensateReceiptPdfUseCase** — plain field parameters:

```java
package com.wpanther.receipt.pdf.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

public interface CompensateReceiptPdfUseCase {
    void compensate(String documentId, String sagaId, SagaStep sagaStep, String correlationId);
}
```

### 3. `ReceiptPdfDocumentService` implements use case interfaces

Refactor `ReceiptPdfDocumentService` to implement `ProcessReceiptPdfUseCase` and `CompensateReceiptPdfUseCase`. It contains ALL orchestration logic:

**`process(...)` method** — moved from `SagaCommandHandler.handle(KafkaReceiptProcessCommand)`:
1. Validate input fields (null/blank checks) → publish failure reply, return
2. Idempotency check via `findByReceiptId(documentId)`
3. If already completed → publish idempotent success, return
4. Retry exhaustion check → publish retry exhausted, return
5. Begin or resume generation
6. Fetch signed XML via `signedXmlFetchPort.fetch(signedXmlUrl)`
7. Generate PDF via `pdfGenerationService.generatePdf(documentNumber, xml)`
8. Store PDF via `pdfStoragePort.store(documentNumber, pdfBytes)`
9. Resolve URL via `pdfStoragePort.resolveUrl(s3Key)`
10. Call `completeGenerationAndPublish(...)` → saves doc, publishes `ReceiptPdfGeneratedEvent`, publishes saga SUCCESS reply
11. On error: delete orphaned PDF, call `failGenerationAndPublish(...)` → publishes saga FAILURE reply
12. Circuit breaker (`CallNotPermittedException`), HTTP errors, generic exceptions all handled with appropriate failure publishing
13. Throws `ReceiptPdfGenerationException` only AFTER saga reply has been committed to outbox (so handler can return normally and Camel commits offset)

**`compensate(...)` method** — moved from `SagaCommandHandler.handle(KafkaReceiptCompensateCommand)`:
1. Find document by `receiptId`
2. Delete document and PDF from storage
3. Publish COMPENSATED reply
4. On error: publish FAILURE reply, rethrow `RuntimeException` for Camel's DLC retry

**Other methods refactored** to accept plain fields:

| Method | New Signature |
|--------|----------------|
| `completeGenerationAndPublish` | `(UUID id, String s3Key, String fileUrl, long fileSize, int previousRetryCount, String documentId, String documentNumber, String sagaId, SagaStep sagaStep, String correlationId)` |
| `failGenerationAndPublish` | `(UUID id, String error, int retryCount, String documentId, String sagaId, SagaStep sagaStep, String correlationId)` |
| `publishIdempotentSuccess` | `(ReceiptPdfDocument doc, String documentId, String documentNumber, String sagaId, SagaStep sagaStep, String correlationId)` |
| `publishRetryExhausted` | `(String sagaId, SagaStep sagaStep, String correlationId, String documentId, String documentNumber)` |
| `publishGenerationFailure` | `(String sagaId, SagaStep sagaStep, String correlationId, String error)` |

### 4. New thin `SagaCommandHandler` in `infrastructure/adapter/in/kafka/`

**Move from:** `application/service/SagaCommandHandler.java`
**To:** `infrastructure/adapter/in/kafka/SagaCommandHandler.java`

The moved handler is a thin adapter implementing both use case interfaces. It ONLY:
- Extracts fields from DTOs
- Calls `receiptPdfDocumentService.process(...)` or `receiptPdfDocumentService.compensate(...)`
- Catches `ReceiptPdfGenerationException` and returns normally (reply was already committed to outbox)
- Propagates other exceptions to trigger Camel's DLC

```java
@Component
@Slf4j
public class SagaCommandHandler implements ProcessReceiptPdfUseCase, CompensateReceiptPdfUseCase {

    private final ReceiptPdfDocumentService pdfDocumentService;

    public SagaCommandHandler(ReceiptPdfDocumentService pdfDocumentService) {
        this.pdfDocumentService = pdfDocumentService;
    }

    @Override
    public void process(String documentId, String documentNumber, String signedXmlUrl,
                       String sagaId, SagaStep sagaStep, String correlationId) throws ReceiptPdfGenerationException {
        log.info("Handling ProcessReceiptPdfCommand for saga {} document {}", sagaId, documentNumber);
        try {
            pdfDocumentService.process(documentId, documentNumber, signedXmlUrl, sagaId, sagaStep, correlationId);
        } catch (ReceiptPdfGenerationException e) {
            log.error("Receipt PDF generation failed for saga {}: {}", sagaId, e.toString());
        }
    }

    @Override
    public void compensate(String documentId, String sagaId, SagaStep sagaStep, String correlationId) {
        log.info("Handling compensation for saga {} document {}", sagaId, documentId);
        pdfDocumentService.compensate(documentId, sagaId, sagaStep, correlationId);
    }
}
```

**DLQ failure notification methods** — accept plain fields (not command objects):

The three `publish*Failure` methods currently on `SagaCommandHandler` accept command objects and are called from `SagaRouteConfig.onPrepareFailure`. These should be refactored to accept plain fields:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void publishOrchestrationFailure(String sagaId, SagaStep sagaStep, String correlationId, Throwable cause) { ... }

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void publishCompensationOrchestrationFailure(String sagaId, SagaStep sagaStep, String correlationId, Throwable cause) { ... }

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void publishOrchestrationFailureForUnparsedMessage(String sagaId, SagaStep sagaStep, String correlationId, Throwable cause) { ... }
```

These stay in `infrastructure/adapter/in/kafka/SagaCommandHandler` since they are infrastructure concerns (DLQ → orchestrator notification).

### 5. Update SagaRouteConfig

- Change `KafkaReceiptProcessCommand` → `ProcessReceiptPdfCommand` (from `dto/`)
- Change `KafkaReceiptCompensateCommand` → `CompensateReceiptPdfCommand` (from `dto/`)
- Route processors extract fields from DTOs and call `processUseCase.process(docId, docNum, signedXmlUrl, sagaId, step, corrId)` directly
- `onPrepareFailure` instanceof checks updated to new DTO names
- `publishOrchestrationFailure(cmd, cause)` calls updated to `publishOrchestrationFailure(cmd.getSagaId(), cmd.getSagaStep(), cmd.getCorrelationId(), cause)` with plain fields

### 6. Delete KafkaCommandMapper

No-op pass-through. Remove after confirming no other usage.

### 7. Delete old files

- `application/service/SagaCommandHandler.java` (original location)
- `infrastructure/adapter/in/kafka/KafkaReceiptProcessCommand.java`
- `infrastructure/adapter/in/kafka/KafkaReceiptCompensateCommand.java`
- `infrastructure/adapter/in/kafka/KafkaCommandMapper.java`
- `application/usecase/ProcessReceiptPdfUseCase.java`
- `application/usecase/CompensateReceiptPdfUseCase.java`

## Files to Modify

| Action | File |
|--------|------|
| Create | `infrastructure/adapter/in/kafka/dto/ProcessReceiptPdfCommand.java` |
| Create | `infrastructure/adapter/in/kafka/dto/CompensateReceiptPdfCommand.java` |
| Create | `application/port/in/ProcessReceiptPdfUseCase.java` |
| Create | `application/port/in/CompensateReceiptPdfUseCase.java` |
| Move | `application/service/SagaCommandHandler.java` → `infrastructure/adapter/in/kafka/SagaCommandHandler.java` (thin adapter) |
| Rewrite | `ReceiptPdfDocumentService.java` (implements use cases, contains all orchestration) |
| Modify | `SagaRouteConfig.java` (new DTO names, plain field calls, DLQ plain field signatures) |
| Delete | `infrastructure/adapter/in/kafka/KafkaReceiptProcessCommand.java` |
| Delete | `infrastructure/adapter/in/kafka/KafkaReceiptCompensateCommand.java` |
| Delete | `infrastructure/adapter/in/kafka/KafkaCommandMapper.java` |
| Delete | `application/usecase/ProcessReceiptPdfUseCase.java` |
| Delete | `application/usecase/CompensateReceiptPdfUseCase.java` |
| Delete | `application/service/SagaCommandHandler.java` (original location) |

## Testing Strategy

1. **Unit tests** — Update test classes for moved/modified classes:
   - `SagaCommandHandlerTest` — update imports for thin adapter, verify delegation
   - `ReceiptPdfDocumentServiceTest` — update for new use case method signatures
   - DTO tests — rename references

2. **SagaRouteConfigTest** — update DTO class references and method calls

3. **Kafka consumer tests** — verify `ProcessReceiptPdfCommand` / `CompensateReceiptPdfCommand` deserialization

4. **Full compile + test:** `mvn clean compile && mvn clean test`

## Exception Contract (from Reference)

The exception contract matches the reference implementation:

- `ReceiptPdfGenerationException` is thrown by `process()` **only after** the saga reply (SUCCESS or FAILURE) has been committed to the outbox. The handler catches it and returns normally so Camel commits the Kafka offset.

- `compensate()` commits a COMPENSATED reply to the outbox and returns normally on success. On failure it commits a FAILURE reply (via `publishCompensationFailure`) and throws a `RuntimeException` to trigger Camel's DLC retry. Retries are safe because `deleteById` is idempotent.

- Any **other** exception (e.g., a DB outage preventing outbox write) propagates uncaught to the DLC — this is intentional as it signals the reply was not durable.

## Verification

```bash
mvn clean compile   # Verify no compilation errors
mvn clean test      # Verify all tests pass
```

## Scope

This refactor addresses **only** `receipt-pdf-generation-service`. Other services with similar patterns should be audited separately.