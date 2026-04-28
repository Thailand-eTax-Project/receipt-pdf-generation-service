# Receipt PDF Generation Service

A microservice for generating PDF/A-3b documents for Thai e-Tax receipts with embedded signed XML. Built with Hexagonal Architecture (Ports/Adapters pattern).

## Overview

This service consumes saga commands from Kafka, generates PDF/A-3b receipts using Apache FOP, embeds the signed XML using PDFBox, and stores the result in MinIO (S3-compatible storage). It implements the transactional outbox pattern for reliable event publishing.

## Architecture

```
com.wpanther.receipt.pdf/
├── domain/          # Business logic, aggregate root (ReceiptPdfDocument), state machine
├── application/     # Use cases (ProcessReceiptPdf, CompensateReceiptPdf), services (SagaCommandHandler)
│   └── port/out/   # Port interfaces (PdfEventPort, PdfStoragePort, SagaReplyPort, SignedXmlFetchPort)
└── infrastructure/  # Adapters: Kafka consumer, PDF generation (FOP+PDFBox), MinIO storage, outbox messaging
```

**Key pattern**: Domain layer defines ports as interfaces; Infrastructure layer provides adapter implementations.

## Technology Stack

- **Java 21** with Spring Boot 3.2.5
- **Apache Camel 4.14.4** for Kafka integration
- **Apache FOP 2.9** for XSL-FO → PDF rendering
- **Apache PDFBox 3.0.1** for PDF/A-3b conversion with embedded XML
- **PostgreSQL** with Flyway for persistence
- **MinIO** (S3-compatible) for PDF storage
- **Resilience4j** for circuit breakers

## Kafka Integration

### Consumed Topics

| Topic | Description |
|-------|-------------|
| `saga.command.receipt-pdf` | Process commands (generate PDF) |
| `saga.compensation.receipt-pdf` | Compensation commands (rollback) |

### Produced Topics

| Topic | Description |
|-------|-------------|
| `pdf.generated.receipt` | Notification when PDF is generated |
| `saga.reply.receipt-pdf` | Reply to saga orchestrator |
| `pdf.generation.receipt.dlq` | Dead letter queue for failed messages |

## State Machine

```
PENDING → GENERATING → COMPLETED
                  ↘ FAILED
(any) → COMPENSATED (via compensation flow)
```

## PDF Generation Pipeline

1. `SagaCommandHandler` receives Kafka command
2. Idempotency check via `receiptId` unique constraint
3. `FopReceiptPdfGenerator` renders XSL-FO → PDF using semaphore for concurrency control
4. `PdfA3Converter` embeds signed XML into PDF/A-3b using PDFBox
5. `MinioStorageAdapter` stores the PDF
6. Event published via transactional outbox pattern

## Configuration

All settings are in `application.yml` with environment variable overrides:

| Property | Environment Variable | Default | Description |
|----------|----------------------|---------|-------------|
| `server.port` | - | 8095 | HTTP server port |
| `spring.datasource.url` | DB_HOST, DB_PORT, DB_NAME | localhost:5432/receiptpdf_db | PostgreSQL connection |
| `app.kafka.bootstrap-servers` | KAFKA_BROKERS | localhost:9092 | Kafka brokers |
| `app.minio.endpoint` | MINIO_ENDPOINT | http://localhost:9000 | MinIO endpoint |
| `app.minio.bucket-name` | MINIO_BUCKET_NAME | receipts | MinIO bucket |
| `app.pdf.generation.max-retries` | PDF_GENERATION_MAX_RETRIES | 3 | Max retry attempts |
| `app.pdf.generation.max-concurrent-renders` | PDF_MAX_CONCURRENT_RENDERS | 3 | Max concurrent FOP renders |

## Building

```bash
# Run all tests
mvn clean test

# Run single test class
mvn clean test -Dtest=ClassName

# Run single test method
mvn clean test -Dtest=ClassName#methodName

# Build JAR without running tests
mvn clean package -DskipTests

# Run database migrations
mvn flyway:migrate

# Run locally
mvn spring-boot:run
```

## Running

### Prerequisites

- PostgreSQL database (`receiptpdf_db`)
- Kafka cluster
- MinIO instance
- Eureka registry (optional, for service discovery)

### Environment Variables

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=receiptpdf_db
export DB_USERNAME=postgres
export DB_PASSWORD=postgres

export KAFKA_BROKERS=localhost:9092

export MINIO_ENDPOINT=http://localhost:9000
export MINIO_ACCESS_KEY=minioadmin
export MINIO_SECRET_KEY=minioadmin
export MINIO_BUCKET_NAME=receipts

export EUREKA_URL=http://localhost:8761/eureka/
```

### Database Migration

On first run, Flyway automatically creates:
- `receipt_pdf_documents` — stores PDF generation state
- `outbox_events` — transactional outbox table

## Actuator Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health check |
| `/actuator/prometheus` | Prometheus metrics |
| `/actuator/camelroutes` | Camel route status |

## Observability

### Metrics (Prometheus)

- `pdf.fop.render` — FOP render timing
- `pdf.fop.size.bytes` — Generated PDF sizes
- `pdf.fop.render.available_permits` — Available concurrent render slots
- Circuit breaker state for MinIO and signed XML fetch

### Tracing

Distributed tracing via Micrometer → OpenTelemetry → OTLP exporter.

## XSL Template

The service uses `src/main/resources/xsl/receipt-direct.xsl` for Thai e-Tax receipt formatting with proper namespace handling for `rsm:Receipt_CrossIndustryInvoice`.

## Fonts

Thai fonts bundled in `src/main/resources/fonts/`:
- THSarabunNew.ttf (Regular, Bold, Italic, BoldItalic)
- NotoSansThaiLooped (Regular, Bold)

## Concurrency

`FopReceiptPdfGenerator` uses a fair `Semaphore` to limit concurrent FOP renders (default: 3). Requests beyond the limit queue up — no timeout or rejection, just backpressure. Each FOP render uses ~50–200 MB heap.
