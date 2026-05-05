# Receipt PDF Generation Service

A Spring Boot microservice for generating PDF/A-3b documents for Thai e-Tax receipts with embedded signed XML. Built with **Hexagonal Architecture (Ports/Adapters)** and **Saga Orchestration Pattern**.

**Port:** 8095 | **Database:** PostgreSQL `receiptpdf_db` | **Kafka Topics:** `saga.command.receipt-pdf`, `saga.reply.receipt-pdf`, `pdf.generated.receipt`

## Tech Stack

Java 21 · Spring Boot 3.2.5 · Apache Camel 4.x · Apache FOP 2.x · Apache PDFBox 3.x · PostgreSQL · MinIO (S3) · Resilience4j

## Build & Run

```bash
# Prerequisites: build teda and saga-commons libraries
cd ../../../../teda && mvn clean install
cd ../../../../saga-commons && mvn clean install

# Build
mvn clean package

# Run tests (ALWAYS use 'clean' to avoid Lombok staleness)
mvn clean test

# Run with coverage (JaCoCo 90% requirement)
mvn verify

# Run locally
mvn spring-boot:run

# Single test
mvn test -Dtest=CamelRouteConfigTest -Dtest=FopReceiptPdfGeneratorTest#constructor_semaphorePermitsMatchConfiguration
```

## Architecture

### DDD Layer Structure

```
com.wpanther.receipt.pdf/
├── domain/
│   ├── model/
│   │   ├── ReceiptPdfDocument.java         # Aggregate root (builder, no Lombok)
│   │   └── GenerationStatus.java           # PENDING | GENERATING | COMPLETED | FAILED
│   ├── repository/
│   │   └── ReceiptPdfDocumentRepository.java
│   ├── service/
│   │   └── ReceiptPdfGenerationService.java # Interface: generatePdf(number, xml)
│   ├── constants/
│   │   └── PdfGenerationConstants.java
│   └── exception/
│       └── ReceiptPdfGenerationException.java
│
├── application/
│   ├── port/in/
│   │   ├── ProcessReceiptPdfUseCase.java        # handle(docId, docNumber, signedXmlUrl, ...)
│   │   │   └── class ReceiptPdfGenerationException extends Exception
│   │   └── CompensateReceiptPdfUseCase.java     # handle(docId, sagaId, ...)
│   ├── dto/event/
│   │   └── ReceiptPdfGeneratedEvent.java        # Extends TraceEvent
│   └── service/
│       └── ReceiptPdfDocumentService.java       # All orchestration logic; short @Transactional methods
│
└── infrastructure/
    ├── adapter/in/kafka/
    │   ├── SagaRouteConfig.java                # Apache Camel routes (Kafka consumers)
    │   ├── SagaCommandHandler.java             # Thin driving adapter — routes DTOs to service
    │   └── dto/
    │       ├── ProcessReceiptPdfCommand.java    # extends SagaCommand + Jackson (Kafka DTO)
    │       └── CompensateReceiptPdfCommand.java
    ├── adapter/out/
    │   ├── pdf/
    │   │   ├── ReceiptPdfGenerationServiceImpl.java  # FOP + PDFBox pipeline
    │   │   ├── FopReceiptPdfGenerator.java           # XSL-FO → PDF (thread-safe)
    │   │   ├── PdfA3Converter.java                   # PDF → PDF/A-3 + embedded XML
    │   │   └── ThaiAmountWordsConverter.java         # Thai baht → words (e.g. 5350 → ห้าพันสามร้อยห้าสิบบาทถ้วน)
    │   ├── storage/
    │   │   ├── MinioStorageAdapter.java              # Implements PdfStoragePort (Resilience4j CB)
    │   │   └── MinioCleanupService.java
    │   ├── client/
    │   │   └── RestTemplateSignedXmlFetcher.java     # Downloads signed XML from signedXmlUrl
    │   ├── persistence/
    │   │   ├── ReceiptPdfDocumentEntity.java
    │   │   ├── JpaReceiptPdfDocumentRepository.java
    │   │   └── outbox/                              # Outbox JPA entities + repos
    │   └── messaging/
    │       ├── EventPublisher.java                   # Implements PdfEventPort via outbox
    │       ├── SagaReplyPublisher.java               # Implements SagaReplyPort via outbox
    │       └── OutboxConstants.java
    ├── config/
    │   ├── MinioConfig.java
    │   ├── OutboxConfig.java
    │   ├── RestTemplateConfig.java
    │   └── FontHealthCheck.java                      # Thai font validation at startup
    └── metrics/
        └── PdfGenerationMetrics.java
```

## Saga Pipeline

```
[Orchestrator] → saga.command.receipt-pdf → SagaRouteConfig (Camel)
                                                 ↓
                                    SagaCommandHandler.handleProcessCommand()
                                      ├── [tx] beginGeneration()       PENDING→GENERATING
                                      ├── RestTemplate: download signedXml from signedXmlUrl
                                      ├── FOP + PDFBox: generate PDF/A-3 bytes
                                      ├── MinioStorageAdapter: upload → s3Key + fileUrl
                                      └── [tx] completeGenerationAndPublish()
                                             ├── GENERATING→COMPLETED
                                             ├── outbox → saga.reply.receipt-pdf (SUCCESS)
                                             └── outbox → pdf.generated.receipt

[Orchestrator] → saga.compensation.receipt-pdf → SagaCommandHandler.handleCompensation()
                                                      ├── [tx] deleteById() + flush
                                                      ├── MinioStorageAdapter.delete() (best-effort)
                                                      └── [tx] publishCompensated()
                                                             outbox → saga.reply.receipt-pdf (COMPENSATED)
```

## Transaction Boundary Design

`ReceiptPdfDocumentService` holds **no open transaction** during long-running work:

1. **Short TX 1** — `beginGeneration()`: create PENDING→GENERATING record (~10 ms)
2. **No transaction** — download signed XML (network), run FOP+PDFBox (CPU, ~1–3 s), upload to MinIO (network)
3. **Short TX 2** — `completeGenerationAndPublish()` or `failGenerationAndPublish()`: mark COMPLETED/FAILED + write both outbox events atomically (~100 ms)

This keeps Hikari connections free during CPU and I/O, preventing pool exhaustion under concurrent load.

## Kafka Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `saga.command.receipt-pdf` | Consume | Process commands from Orchestrator |
| `saga.compensation.receipt-pdf` | Consume | Compensation commands from Orchestrator |
| `saga.reply.receipt-pdf` | Produce (outbox) | SUCCESS / FAILURE / COMPENSATED replies to Orchestrator |
| `pdf.generated.receipt` | Produce (outbox) | Notification event (header: `documentType=RECEIPT`, `correlationId`) |
| `pdf.generation.receipt.dlq` | Produce (DLQ) | Dead-letter queue after 3 Camel retries |
| `document.archive` | Produce (outbox) | Fire-and-forget archival (UNSIGNED_PDF stored by document-storage-service) |

### Input Command

```json
{
  "eventId": "uuid",
  "occurredAt": "2024-01-15T10:30:00Z",
  "eventType": "saga.command.receipt-pdf",
  "version": 1,
  "sagaId": "uuid",
  "sagaStep": "GENERATE_RECEIPT_PDF",
  "correlationId": "uuid",
  "documentId": "uuid",
  "documentNumber": "RCP-2024-001",
  "signedXmlUrl": "http://localhost:9000/receipts/signed-xml-key"
}
```

The service downloads signed XML from `signedXmlUrl` at runtime — no inline XML in the command.

### Output Reply (SUCCESS)

```json
{
  "sagaId": "uuid",
  "sagaStep": "GENERATE_RECEIPT_PDF",
  "correlationId": "uuid",
  "status": "SUCCESS",
  "pdfUrl": "http://localhost:9000/receipts/2024/01/15/receipt-RCP-001-uuid.pdf",
  "pdfSize": 123456
}
```

### Output Notification

```json
{
  "documentId": "uuid",
  "documentNumber": "RCP-2024-001",
  "documentUrl": "http://localhost:9000/receipts/...",
  "fileSize": 123456,
  "xmlEmbedded": true,
  "correlationId": "uuid"
}
```

## Domain Model State Machine

```
ReceiptPdfDocument aggregate root:
  PENDING → startGeneration() → GENERATING
  GENERATING → markCompleted(path, url, size) → COMPLETED
  Any state → markFailed(message) → FAILED
```

State transition methods enforce invariants and throw `IllegalStateException` on invalid transitions.

## PDF Generation Pipeline

```
ReceiptPdfGenerationServiceImpl.generatePdf(receiptNumber, signedXml)
  ├── 1. FopReceiptPdfGenerator.generatePdf(signedXml)
  │      Acquires renderSemaphore (fair, cap = PDF_MAX_CONCURRENT_RENDERS, default 3)
  │      compiledTemplate.newTransformer() — per-call Transformer (thread-safe design)
  │      xsl/receipt-direct.xsl → Apache FOP → base PDF bytes
  │      XSL navigates rsm:/ram: namespaces directly from signed CII XML
  │      Releases renderSemaphore in finally
  └── 2. PdfA3Converter.convertToPdfA3(basePdf, signedXml, xmlFilename, receiptNumber)
         PDFBox: XMP metadata (PDF/A-3B), ICC colour profile (sRGB.icc)
         Embeds signedXml as attachment (AFRelationship=Source)
         Returns PDF/A-3 bytes
```

`ThaiAmountWordsConverter.toWords(grandTotal)` computes Thai baht-in-words in Java; injected as `$amountInWords` XSLT parameter.

### FOP Thread-Safety

`FopReceiptPdfGenerator` pre-compiles `receipt-direct.xsl` to a `Templates` object at startup (JAXP contract: thread-safe). Each `generatePdf()` call creates its own `Transformer` via `compiledTemplate.newTransformer()`. A fair `Semaphore` caps concurrent renders independently of the Kafka consumer count.

## Idempotency and Retry Handling

`ReceiptPdfDocumentService.process()` handles four cases:

1. **Already COMPLETED**: re-publishes SUCCESS reply + notification without regenerating
2. **Previously FAILED, retries not exhausted**: deletes old record (flush enforces DELETE-before-INSERT on the unique `receipt_id` key), then starts fresh
3. **Max retries exceeded** (`retryCount >= PDF_GENERATION_MAX_RETRIES`): publishes FAILURE reply, aborts
4. **New**: normal processing path

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `receiptpdf_db` | Database name |
| `KAFKA_BROKERS` | `localhost:9092` | Kafka bootstrap servers |
| `KAFKA_CONSUMERS_COUNT` | `3` | Camel concurrent consumers |
| `MINIO_ENDPOINT` | `http://localhost:9000` | MinIO endpoint |
| `MINIO_BUCKET_NAME` | `receipts` | Target bucket |
| `PDF_GENERATION_MAX_RETRIES` | `3` | Saga retry limit per receipt |
| `PDF_MAX_CONCURRENT_RENDERS` | `3` | Max concurrent FOP renders (Semaphore) |

## Running

### Prerequisites

- PostgreSQL with `receiptpdf_db` database created
- Kafka on `localhost:9092`
- MinIO with bucket `receipts` (or `${MINIO_BUCKET_NAME}`)

```bash
export DB_HOST=localhost
export KAFKA_BROKERS=localhost:9092
export MINIO_ENDPOINT=http://localhost:9000
export MINIO_BUCKET_NAME=receipts
mvn spring-boot:run
```

Flyway automatically runs migrations on startup — `receipt_pdf_documents` table and `outbox_events` table.

## Error Handling

`SagaRouteConfig` uses Dead Letter Channel:
- 3 retries, 1 s initial delay, exponential backoff ×2, max delay 10 s
- On exhaustion: `sagaCommandHandler.publishOrchestrationFailure()` (`REQUIRES_NEW` tx) notifies Orchestrator, then message → DLQ

## Differences from invoice-pdf-generation-service

| Aspect | invoice-pdf-generation-service | receipt-pdf-generation-service |
|--------|--------------------------------|---------------------------------|
| Port | 8090 | 8095 |
| Package | `com.wpanther.invoice.pdf` | `com.wpanther.receipt.pdf` |
| Database | `invoicepdf_db` / `invoice_pdf_documents` | `receiptpdf_db` / `receipt_pdf_documents` |
| Kafka Consume | `saga.command.invoice-pdf` | `saga.command.receipt-pdf` |
| Saga Reply | `saga.reply.invoice-pdf` | `saga.reply.receipt-pdf` |
| Notification | `pdf.generated.invoice` | `pdf.generated.receipt` |
| DLQ | `pdf.generation.invoice.dlq` | `pdf.generation.receipt.dlq` |
| Storage bucket | `invoices` | `receipts` |
| Field names | `documentId`, `documentNumber` | `receiptId`, `receiptNumber` |
| XSL template | `xsl/invoice.xsl` | `xsl/receipt-direct.xsl` |
| XML root element | `/rsm:Invoice_CrossIndustryInvoice` | `/rsm:Receipt_CrossIndustryInvoice` |
| Document type header | `INVOICE` | `RECEIPT` |
| Fonts | THSarabunNew only | THSarabunNew + NotoSansThaiLooped |

## Resources

| Resource | Path |
|----------|------|
| XSL-FO template | `src/main/resources/xsl/receipt-direct.xsl` |
| FOP config | `src/main/resources/fop/fop.xconf` |
| ICC profile | `src/main/resources/icc/sRGB.icc` |
| Fonts | `src/main/resources/fonts/` (THSarabunNew + NotoSansThaiLooped) |
| DB migration | `src/main/resources/db/migration/V1__create_receipt_pdf_tables.sql` |

## Testing

Tests use H2 in-memory database (`application-test.yml`). Testcontainers/PostgreSQL for integration tests.

| Class | Covers |
|-------|--------|
| `ReceiptPdfDocumentServiceTest` | Transactional service methods, idempotency, retry exhaustion |
| `SagaCommandHandlerTest` | Command orchestration logic (mocked deps) |
| `ReceiptPdfDocumentTest` | Domain model state machine + invariants |
| `FopReceiptPdfGeneratorTest` | Constructor, semaphore permits, malformed XML |
| `ReceiptPdfGenerationServiceImplTest` | FOP+PDFBox pipeline (mocked) |
| `PdfA3ConverterTest` | PDF/A-3 conversion |
| `ThaiAmountWordsConverterTest` | Thai baht amount to words |
| `RestTemplateSignedXmlFetcherTest` | REST client with circuit breaker |
| `MinioStorageAdapterTest` | S3 upload / delete / resolveUrl |
| `EventPublisherTest` | Outbox writes for pdf-generated event |
| `SagaReplyPublisherTest` | Outbox writes for saga replies |
| `CamelRouteConfigTest` | Camel route wiring and Kafka command serialization |

## Common Pitfalls

1. **Lombok staleness**: Always run `mvn clean test` (not `mvn test`) — stale Lombok classes cause compile errors
2. **saga-commons not installed**: `cd ../../../../saga-commons && mvn clean install` before building
3. **teda not installed**: `cd ../../../../teda && mvn clean install`
4. **MinIO bucket missing**: Ensure bucket `receipts` exists before running
5. **Wrong Kafka topics**: Consumes `saga.command.receipt-pdf` — replies go to `saga.reply.receipt-pdf`
6. **Font files missing**: THSarabunNew + NotoSansThaiLooped must be in `src/main/resources/fonts/` and referenced in `fop.xconf`
7. **ICC profile missing**: `sRGB.icc` must be in `src/main/resources/icc/` for PDF/A compliance
8. **FOP is CPU-heavy**: Each render ~50–200 MB heap and ~1–3 s. Tune `PDF_MAX_CONCURRENT_RENDERS` independently of `KAFKA_CONSUMERS_COUNT`
9. **Outbox requires active transaction**: `EventPublisher` and `SagaReplyPublisher` use `Propagation.MANDATORY` — must be called from within a `@Transactional` method
10. **Testcontainers with Podman**: `export DOCKER_HOST="unix:///run/user/$(id -u)/podman/podman.sock"` before running `mvn test`
11. **Field naming**: Domain model uses `receiptId`/`receiptNumber` (not `documentId`/`documentNumber`) — match across all layers

## No REST API

This service is event-driven only. No REST endpoints beyond Spring Actuator: `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/camelroutes`.

## External Dependencies

- **teda library** (`com.wpanther:thai-etax-invoice`): Used by upstream services
- **saga-commons library** (`com.wpanther:saga-commons`): `OutboxService`, `SagaCommand` base class, `SagaStep` enum — must be installed before building
- **eidasremotesigning** (`localhost:9000`): Used by XML Signing Service (upstream) — not called by this service directly