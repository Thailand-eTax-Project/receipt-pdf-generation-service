# Receipt PDF Generation Service — Design Spec

**Date:** 2026-04-28  
**Status:** Approved  
**Scope:** `receipt-pdf-generation-service` (new) + `GENERATE_RECEIPT_PDF` SagaStep in `saga-commons`

---

## 1. Overview

A new Spring Boot microservice that generates PDF/A-3 documents for Thai e-Tax Receipt (`Receipt_CrossIndustryInvoice`) documents. It participates in the Saga Orchestration pipeline, consuming commands from the orchestrator and replying via the transactional outbox pattern.

The service is a full hexagonal port of `taxinvoice-pdf-generation-service` (port 8089) with all production features. Only receipt-specific identifiers, namespaces, and the XSL-FO template differ.

---

## 2. Saga Commons Change

**File:** `saga-commons/src/main/java/com/wpanther/saga/domain/enums/SagaStep.java`

Add one new enum value after `GENERATE_TAX_INVOICE_PDF`:

```java
/**
 * Receipt PDF generation via receipt-pdf-generation-service.
 */
GENERATE_RECEIPT_PDF("generate-receipt-pdf", "Receipt PDF Generation Service"),
```

---

## 3. Service Identity

| Property | Value |
|----------|-------|
| Port | `8095` |
| Artifact ID | `receipt-pdf-generation-service` |
| Package root | `com.wpanther.receipt.pdf` |
| Main class | `ReceiptPdfGenerationServiceApplication` |
| Database | `receiptpdf_db` (PostgreSQL) |
| MinIO bucket | `receipts` (env: `MINIO_BUCKET_NAME`) |
| Camel app name | `receipt-pdf-generation-camel` |

---

## 4. Architecture — Hexagonal (Port/Adapter)

```
com.wpanther.receipt.pdf/
├── domain/
│   ├── model/
│   │   ├── ReceiptPdfDocument.java          # Aggregate root
│   │   └── GenerationStatus.java            # PENDING → GENERATING → COMPLETED/FAILED
│   ├── repository/
│   │   └── ReceiptPdfDocumentRepository.java
│   ├── service/
│   │   └── ReceiptPdfGenerationService.java  # Port interface
│   ├── exception/
│   │   └── ReceiptPdfGenerationException.java
│   └── constants/
│       └── PdfGenerationConstants.java
│
├── application/
│   ├── service/
│   │   ├── SagaCommandHandler.java
│   │   └── ReceiptPdfDocumentService.java
│   ├── usecase/
│   │   ├── ProcessReceiptPdfUseCase.java
│   │   └── CompensateReceiptPdfUseCase.java
│   └── port/out/
│       ├── PdfEventPort.java
│       ├── PdfStoragePort.java
│       ├── SagaReplyPort.java
│       └── SignedXmlFetchPort.java
│
└── infrastructure/
    ├── adapter/in/kafka/
    │   ├── KafkaReceiptProcessCommand.java    # extends SagaCommand
    │   ├── KafkaReceiptCompensateCommand.java # extends SagaCommand
    │   ├── KafkaCommandMapper.java
    │   └── SagaRouteConfig.java               # Apache Camel routes
    ├── adapter/out/
    │   ├── client/
    │   │   └── RestTemplateSignedXmlFetcher.java  # Circuit breaker: signedXmlFetch
    │   ├── messaging/
    │   │   ├── EventPublisher.java
    │   │   ├── SagaReplyPublisher.java
    │   │   ├── ReceiptPdfGeneratedEvent.java
    │   │   ├── ReceiptPdfReplyEvent.java       # SUCCESS: pdfUrl + pdfSize
    │   │   └── OutboxConstants.java
    │   ├── pdf/
    │   │   ├── FopReceiptPdfGenerator.java     # XPath + FOP render (Semaphore)
    │   │   ├── PdfA3Converter.java             # PDFBox: PDF → PDF/A-3 + XML embed
    │   │   ├── ThaiAmountWordsConverter.java   # Baht → Thai words
    │   │   └── ReceiptPdfGenerationServiceImpl.java
    │   ├── persistence/
    │   │   ├── ReceiptPdfDocumentEntity.java
    │   │   ├── JpaReceiptPdfDocumentRepository.java
    │   │   ├── ReceiptPdfDocumentRepositoryAdapter.java
    │   │   └── outbox/
    │   │       ├── OutboxEventEntity.java
    │   │       ├── SpringDataOutboxRepository.java
    │   │       └── JpaOutboxEventRepository.java
    │   └── storage/
    │       ├── MinioStorageAdapter.java        # Circuit breaker: minio
    │       └── MinioCleanupService.java        # Scheduled orphan cleanup
    ├── config/
    │   ├── MinioConfig.java
    │   ├── OutboxConfig.java
    │   ├── RestTemplateConfig.java
    │   └── FontHealthCheck.java
    └── metrics/
        └── PdfGenerationMetrics.java
```

---

## 5. Kafka Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `saga.command.receipt-pdf` | Consume | Process command from Orchestrator |
| `saga.compensation.receipt-pdf` | Consume | Compensation command from Orchestrator |
| `pdf.generated.receipt` | Produce (outbox) | Notification Service (`documentType=RECEIPT`) |
| `saga.reply.receipt-pdf` | Produce (outbox) | Reply to Orchestrator |
| `pdf.generation.receipt.dlq` | Produce | Dead letter queue |

Consumer group IDs:
- Command: `receipt-pdf-generation-command`
- Compensation: `receipt-pdf-generation-compensation`

---

## 6. Saga Command/Reply Schema

### Input: `KafkaReceiptProcessCommand` (extends `SagaCommand`)

```json
{
  "eventId": "uuid",
  "occurredAt": "...",
  "eventType": "...",
  "version": 1,
  "sagaId": "uuid",
  "sagaStep": "generate-receipt-pdf",
  "correlationId": "uuid",
  "documentId": "uuid",
  "documentNumber": "RCP-2024-001",
  "signedXmlUrl": "http://document-storage/documents/..."
}
```

### Output: `ReceiptPdfReplyEvent` (extends `SagaReply`)

```json
{
  "sagaId": "uuid",
  "sagaStep": "generate-receipt-pdf",
  "correlationId": "uuid",
  "status": "SUCCESS|FAILURE|COMPENSATED",
  "errorMessage": null,
  "pdfUrl": "http://localhost:9000/receipts/2024/01/15/receipt-RCP-2024-001-abc.pdf",
  "pdfSize": 12345
}
```

`pdfUrl` and `pdfSize` present only in SUCCESS replies. Orchestrator stores them in `DocumentMetadata` for the subsequent `PDF_STORAGE` step.

### Output: `ReceiptPdfGeneratedEvent` (outbox → `pdf.generated.receipt`)

```json
{
  "eventId": "uuid",
  "eventType": "pdf.generated.receipt",
  "version": 1,
  "documentId": "uuid",
  "receiptId": "uuid",
  "receiptNumber": "RCP-2024-001",
  "documentUrl": "http://...",
  "fileSize": 12345,
  "xmlEmbedded": true,
  "correlationId": "uuid"
}
```

---

## 7. Domain Model — `ReceiptPdfDocument`

Aggregate root with the same state machine as `TaxInvoicePdfDocument`:

| State transition | Method | Guard |
|-----------------|--------|-------|
| `PENDING → GENERATING` | `startGeneration()` | Must be PENDING |
| `GENERATING → COMPLETED` | `markCompleted(path, url, size)` | Must be GENERATING |
| `any → FAILED` | `markFailed(message)` | — |

Key fields: `receiptId` (unique constraint / idempotency key), `receiptNumber`, `documentPath` (S3 key), `documentUrl` (full MinIO URL), `retryCount` (max 3).

MinIO S3 key pattern: `YYYY/MM/DD/receipt-{receiptNumber}-{uuid}.pdf`

---

## 8. PDF Generation Pipeline

```
KafkaReceiptProcessCommand
        ↓
SagaCommandHandler
        ├── Idempotency check (COMPLETED? re-publish and return SUCCESS)
        ├── Retry limit check (retryCount >= maxRetries? send FAILURE)
        └── ReceiptPdfDocumentService.generatePdf()
                ├── Create domain aggregate (PENDING → GENERATING)
                ├── ReceiptPdfGenerationServiceImpl.generatePdf()
                │   ├── RestTemplateSignedXmlFetcher.fetch(signedXmlUrl) → signedXml
                │   ├── XPath on signedXml (rsm:/ram: receipt namespaces)
                │   │     → extract GrandTotalAmount
                │   ├── ThaiAmountWordsConverter.toWords(grandTotal) → amountInWords
                │   ├── FopReceiptPdfGenerator (amountInWords as XSLT param) → base PDF
                │   └── PdfA3Converter → PDF/A-3b with embedded XML
                ├── MinioStorageAdapter.store(pdf, key) → pdfUrl
                └── markCompleted() → COMPLETED
        ├── EventPublisher → outbox_events (pdf.generated.receipt)
        └── SagaReplyPublisher → outbox_events (saga.reply.receipt-pdf)
```

### Compensation Flow

```
KafkaReceiptCompensateCommand
        ↓
SagaCommandHandler.handleCompensation()
        ├── MinioStorageAdapter.delete(documentPath)
        ├── Delete database record
        └── SagaReplyPublisher → COMPENSATED reply (idempotent if no record)
```

---

## 9. XSL-FO Template (`receipt-direct.xsl`)

Adapted from `taxinvoice-direct.xsl`. The only structural changes are:

| Element | TaxInvoice | Receipt |
|---------|-----------|---------|
| `rsm` namespace URI | `...TaxInvoice_CrossIndustryInvoice:2` | `...Receipt_CrossIndustryInvoice:2` |
| `ram` namespace URI | `...TaxInvoice_ReusableAggregateBusinessInformationEntity:2` | `...Receipt_ReusableAggregateBusinessInformationEntity:2` |
| Root match | `/rsm:TaxInvoice_CrossIndustryInvoice` | `/rsm:Receipt_CrossIndustryInvoice` |
| Document title (Thai) | `ใบเสร็จรับเงิน/ใบกำกับภาษี` | `ใบรับ / RECEIPT` |

All XPath paths for `GrandTotalAmount`, seller/buyer parties, line items, and monetary summation are structurally identical — only namespace URIs differ. `amountInWords` XSLT parameter injection is unchanged.

Resources copied as-is from taxinvoice: `fop.xconf`, `sRGB.icc`, Thai font files (`THSarabunNew`, `NotoSansThaiLooped`).

---

## 10. Database — `receiptpdf_db`

Single Flyway migration: `V1__create_receipt_pdf_tables.sql`

Creates in one script:
- `receipt_pdf_documents` table with `receipt_id` unique constraint and `retry_count` column
- `outbox_events` table with compound index on `(status, created_at)`

---

## 11. Configuration (`application.yml`)

All env vars mirror taxinvoice defaults. Receipt-specific values:

| Variable | Default |
|----------|---------|
| `server.port` | `8095` |
| `DB_NAME` | `receiptpdf_db` |
| `MINIO_BUCKET_NAME` | `receipts` |
| `KAFKA_COMMAND_GROUP_ID` | `receipt-pdf-generation-command` |
| `KAFKA_COMPENSATION_GROUP_ID` | `receipt-pdf-generation-compensation` |

All other variables (`MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `KAFKA_BROKERS`, `PDF_GENERATION_MAX_RETRIES`, `PDF_MAX_CONCURRENT_RENDERS`, `PDF_MAX_SIZE_BYTES`, `REST_CLIENT_CONNECT_TIMEOUT`, `REST_CLIENT_READ_TIMEOUT`, `FONT_HEALTH_CHECK_ENABLED`, etc.) use the same defaults as taxinvoice.

---

## 12. Testing Strategy

90% JaCoCo line coverage requirement. H2 in-memory DB, Flyway disabled in `application-test.yml`. Simplified `fop.xconf` for tests (no PDF/A mode, auto-detect fonts — no Thai font files required).

| Test class | What it covers |
|------------|---------------|
| `SagaCommandHandlerTest` | success, idempotency, max retries, generation failure, compensation success, idempotent compensation, compensation failure |
| `CamelRouteConfigTest` | JSON serialization/deserialization of all event types |
| `FopReceiptPdfGeneratorTest` | constructor validation, semaphore, valid/malformed XML, size limit, thread interruption, URI resolution, font availability |
| `PdfA3ConverterTest` | constructor, null/empty PDF, exception constructors |
| `MinioStorageAdapterTest` | upload, delete, URL resolution, Thai chars, filename sanitization |
| `ReceiptPdfDocumentTest` | state machine transitions, invariants, retry counting |
| `ReceiptPdfDocumentServiceTest` | transactional service methods |
| `EventPublisherTest` | outbox event publishing |
| `SagaReplyPublisherTest` | outbox reply publishing |
| `RestTemplateSignedXmlFetcherTest` | REST client with circuit breaker |
| `KafkaCommandMapperTest` | command mapping |
| `MinioCleanupServiceTest` | cleanup scheduling |
| `FontHealthCheckTest` | font validation at startup |

No embedded Kafka integration tests. No REST API — Spring Actuator only (`/actuator/health`, `/actuator/metrics`, `/actuator/camelroutes`, `/actuator/prometheus`).

---

## 13. Out of Scope

- Orchestrator changes (routing `GENERATE_RECEIPT_PDF` commands) — separate task
- `receipt-processing-service` changes
- Integration tests with embedded Kafka
