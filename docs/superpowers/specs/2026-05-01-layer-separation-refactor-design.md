# receipt-pdf-generation-service Layer Separation Refactoring

**Date:** 2026-05-01
**Author:** Claude Code
**Status:** Draft

## Context

`receipt-pdf-generation-service` needs to align with the hexagonal/port-adapters architecture pattern established in `taxinvoice-processing-service` (the reference implementation). The goal is ensuring saga infrastructure types (Kafka DTOs, Jackson annotations) do not leak into the application or domain layers.

## Problem Statement

Two architectural violations exist:

1. **Use case interfaces accept command objects** — `ProcessReceiptPdfUseCase.handle(KafkaReceiptProcessCommand)` and `CompensateReceiptPdfUseCase.handle(KafkaReceiptCompensateCommand)` pass infrastructure objects (Kafka deserialization DTOs with Jackson/SagaCommand inheritance) into the application layer. The application layer should only know about plain field parameters.

2. **SagaCommandHandler lives in `application/service/`** — it belongs in `infrastructure/adapter/in/kafka/` as the driving adapter that receives Kafka messages and routes to use cases.

This creates tight coupling: if the Kafka transport changes (e.g., to HTTP), the application layer would need to change too.

## Target Architecture

```
infrastructure/adapter/in/kafka/
├── dto/
│   ├── ProcessReceiptPdfCommand       ← renamed from KafkaReceiptProcessCommand
│   └── CompensateReceiptPdfCommand    ← renamed from KafkaReceiptCompensateCommand
├── SagaCommandHandler                 ← moved from application/service/
└── SagaRouteConfig                    ← (unchanged)

application/port/in/
├── ProcessReceiptPdfUseCase           ← refactored: plain field parameters
└── CompensateReceiptPdfUseCase        ← refactored: plain field parameters

application/service/
└── ReceiptPdfDocumentService          ← (unchanged — method signatures refactored)

infrastructure/adapter/out/messaging/
├── SagaReplyPublisher                 ← (unchanged — inline ReceiptPdfReplyEvent factory)
├── EventPublisher                     ← (unchanged)
└── ReceiptPdfGeneratedEvent           ← (unchanged — already correct location)
```

**Key rule:** No command objects flow into application or domain layers. `SagaCommandHandler` extracts fields from DTOs and passes them as plain parameters to use case interfaces.

## Design

### 1. Rename Kafka DTOs to remove `Kafka` prefix

Move `infrastructure/adapter/in/kafka/KafkaReceiptProcessCommand` → `dto/ProcessReceiptPdfCommand`
Move `infrastructure/adapter/in/kafka/KafkaReceiptCompensateCommand` → `dto/CompensateReceiptPdfCommand`

Package: `com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto`

Rationale: The `dto/` sub-package makes it explicit these are infrastructure deserialization objects. The `Kafka` prefix is redundant — they're already in the `kafka/` package.

### 2. New use case interfaces in `application/port/in/`

**ProcessReceiptPdfUseCase** — refactored from `application/usecase/ProcessReceiptPdfUseCase`:

```java
package com.wpanther.receipt.pdf.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

public interface ProcessReceiptPdfUseCase {
    void handle(String documentId, String documentNumber, String signedXmlUrl,
                String sagaId, SagaStep sagaStep, String correlationId);
}
```

**CompensateReceiptPdfUseCase** — refactored from `application/usecase/CompensateReceiptPdfUseCase`:

```java
package com.wpanther.receipt.pdf.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

public interface CompensateReceiptPdfUseCase {
    void handle(String documentId, String sagaId, SagaStep sagaStep, String correlationId);
}
```

### 3. Move SagaCommandHandler to infrastructure layer

**Move from:** `application/service/SagaCommandHandler.java`
**To:** `infrastructure/adapter/in/kafka/SagaCommandHandler.java`

The handler implements both `ProcessReceiptPdfUseCase` and `CompensateReceiptPdfUseCase`. It:
- Receives `ProcessReceiptPdfCommand` / `CompensateReceiptPdfCommand` from Kafka routes
- Extracts all fields and calls use case methods with plain parameters
- Is the only class that depends on both the Kafka DTO package and the application port interfaces

### 4. Update SagaRouteConfig to use new DTO names

- Change `KafkaReceiptProcessCommand` → `ProcessReceiptPdfCommand` (from `dto/`)
- Change `KafkaReceiptCompensateCommand` → `CompensateReceiptPdfCommand` (from `dto/`)
- Route processors call `processUseCase.handle(docId, docNum, signedXmlUrl, sagaId, step, corrId)` with extracted fields

### 5. Update ReceiptPdfDocumentService method signatures

Methods that currently accept `KafkaReceiptProcessCommand` / `KafkaReceiptCompensateCommand` are updated to accept individual field parameters:

| Method | New Signature |
|--------|----------------|
| `completeGenerationAndPublish` | `(UUID id, String s3Key, String fileUrl, long fileSize, int previousRetryCount, String documentId, String documentNumber, String sagaId, SagaStep sagaStep, String correlationId)` |
| `failGenerationAndPublish` | `(UUID id, String error, int retryCount, String documentId, String documentNumber, String sagaId, SagaStep sagaStep, String correlationId)` |
| `publishIdempotentSuccess` | `(ReceiptPdfDocument doc, String documentId, String documentNumber, String sagaId, SagaStep sagaStep, String correlationId)` |
| `publishRetryExhausted` | `(String sagaId, SagaStep sagaStep, String correlationId, String documentId, String documentNumber)` |
| `publishGenerationFailure` | `(String sagaId, SagaStep sagaStep, String correlationId, String error)` |
| `publishCompensated` | `(String sagaId, SagaStep sagaStep, String correlationId)` |
| `publishCompensationFailure` | `(String sagaId, SagaStep sagaStep, String correlationId, String error)` |
| `buildGeneratedEvent` | `(ReceiptPdfDocument doc, String documentId, String documentNumber, String sagaId, String correlationId)` |

### 6. Delete KafkaCommandMapper

It is a no-op pass-through that provides no value — the handler directly uses DTO getters. Remove after confirming no other usage.

### 7. Delete old files

- `application/usecase/ProcessReceiptPdfUseCase.java` (original location)
- `application/usecase/CompensateReceiptPdfUseCase.java` (original location)
- `application/service/SagaCommandHandler.java` (original location — replaced by infrastructure layer handler)
- `infrastructure/adapter/in/kafka/KafkaReceiptProcessCommand.java`
- `infrastructure/adapter/in/kafka/KafkaReceiptCompensateCommand.java`
- `infrastructure/adapter/in/kafka/KafkaCommandMapper.java`

## Files to Modify

| Action | File |
|--------|------|
| Create | `infrastructure/adapter/in/kafka/dto/ProcessReceiptPdfCommand.java` |
| Create | `infrastructure/adapter/in/kafka/dto/CompensateReceiptPdfCommand.java` |
| Create | `application/port/in/ProcessReceiptPdfUseCase.java` (new interface) |
| Create | `application/port/in/CompensateReceiptPdfUseCase.java` (new interface) |
| Move | `application/service/SagaCommandHandler.java` → `infrastructure/adapter/in/kafka/SagaCommandHandler.java` |
| Modify | `ReceiptPdfDocumentService.java` (update method signatures) |
| Modify | `SagaRouteConfig.java` (use new DTO names, call use cases with plain params) |
| Delete | `infrastructure/adapter/in/kafka/KafkaReceiptProcessCommand.java` |
| Delete | `infrastructure/adapter/in/kafka/KafkaReceiptCompensateCommand.java` |
| Delete | `infrastructure/adapter/in/kafka/KafkaCommandMapper.java` |
| Delete | `application/usecase/ProcessReceiptPdfUseCase.java` |
| Delete | `application/usecase/CompensateReceiptPdfUseCase.java` |
| Delete | `application/service/SagaCommandHandler.java` (original location) |

## Testing Strategy

1. **Unit tests** — Update all test classes referencing moved/modified classes:
   - `SagaCommandHandlerTest` — update import for moved handler
   - `ReceiptPdfDocumentServiceTest` — update command parameter types
   - Any tests for `KafkaReceiptProcessCommand` / `KafkaReceiptCompensateCommand` — rename to new class names

2. **SagaRouteConfigTest** — update imports and DTO class references

3. **Kafka consumer tests** — verify `ProcessReceiptPdfCommand` / `CompensateReceiptPdfCommand` deserialization still works

4. **Full compile + test:** Run `mvn clean compile && mvn clean test` to verify nothing breaks

## Verification

After refactoring:
```bash
mvn clean compile   # Verify no compilation errors
mvn clean test     # Verify all tests pass
```

## Scope

This refactor addresses **only** `receipt-pdf-generation-service`. Other services with similar patterns should be audited separately.