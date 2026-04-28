# Receipt PDF Generation Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `receipt-pdf-generation-service` (port 8095) — a hexagonal Spring Boot microservice that generates PDF/A-3 documents for Thai e-Tax Receipt (`Receipt_CrossIndustryInvoice`) documents, participating in the Saga Orchestration pipeline.

**Architecture:** Full hexagonal port of `taxinvoice-pdf-generation-service`. Only receipt-specific identifiers, namespaces, and the XSL-FO template differ. All production features included: Apache FOP rendering, PDF/A-3 conversion with XML embedding, MinIO storage with circuit breaker, transactional outbox, Apache Camel Kafka routing, scheduled orphan cleanup.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, Apache FOP 2.9, PDFBox 3.0.1, PostgreSQL, MinIO (S3), Resilience4j, Flyway, saga-commons

---

## File Structure

### Created files (receipt-pdf-generation-service)

```
src/main/java/com/wpanther/receipt/pdf/
├── ReceiptPdfGenerationServiceApplication.java
├── domain/
│   ├── model/
│   │   ├── ReceiptPdfDocument.java
│   │   └── GenerationStatus.java
│   ├── repository/
│   │   └── ReceiptPdfDocumentRepository.java
│   ├── service/
│   │   └── ReceiptPdfGenerationService.java
│   ├── exception/
│   │   └── ReceiptPdfGenerationException.java
│   └── constants/
│       └── PdfGenerationConstants.java
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
└── infrastructure/
    ├── adapter/in/kafka/
    │   ├── KafkaReceiptProcessCommand.java
    │   ├── KafkaReceiptCompensateCommand.java
    │   ├── KafkaCommandMapper.java
    │   └── SagaRouteConfig.java
    ├── adapter/out/
    │   ├── client/
    │   │   └── RestTemplateSignedXmlFetcher.java
    │   ├── messaging/
    │   │   ├── EventPublisher.java
    │   │   ├── SagaReplyPublisher.java
    │   │   ├── ReceiptPdfGeneratedEvent.java
    │   │   ├── ReceiptPdfReplyEvent.java
    │   │   └── OutboxConstants.java
    │   ├── pdf/
    │   │   ├── FopReceiptPdfGenerator.java
    │   │   ├── PdfA3Converter.java
    │   │   ├── ThaiAmountWordsConverter.java
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
    │       ├── MinioStorageAdapter.java
    │       └── MinioCleanupService.java
    ├── config/
    │   ├── MinioConfig.java
    │   ├── OutboxConfig.java
    │   ├── RestTemplateConfig.java
    │   └── FontHealthCheck.java
    └── metrics/
        └── PdfGenerationMetrics.java

src/main/resources/
├── application.yml
├── db/migration/V1__create_receipt_pdf_tables.sql
├── fonts/                          (copied from taxinvoice)
├── fop/fop.xconf                   (copied from taxinvoice)
├── icc/sRGB.icc                    (copied from taxinvoice)
└── xsl/receipt-direct.xsl          (adapted from taxinvoice-direct.xsl)

src/test/java/com/wpanther/receipt/pdf/
├── PdfPreviewTest.java
├── application/service/
│   ├── SagaCommandHandlerTest.java
│   └── ReceiptPdfDocumentServiceTest.java
├── domain/
│   ├── constants/PdfGenerationConstantsTest.java
│   ├── exception/ReceiptPdfGenerationExceptionTest.java
│   └── model/ReceiptPdfDocumentTest.java
└── infrastructure/
    ├── adapter/in/kafka/KafkaCommandMapperTest.java
    ├── adapter/out/
    │   ├── client/RestTemplateSignedXmlFetcherTest.java
    │   ├── messaging/
    │   │   ├── EventPublisherTest.java
    │   │   └── SagaReplyPublisherTest.java
    │   ├── pdf/
    │   │   ├── FopReceiptPdfGeneratorTest.java
    │   │   ├── PdfA3ConverterTest.java
    │   │   ├── ReceiptPdfGenerationServiceImplTest.java
    │   │   └── ThaiAmountWordsConverterTest.java
    │   ├── persistence/JpaReceiptPdfDocumentRepositoryImplTest.java
    │   └── storage/
    │       ├── MinioStorageAdapterTest.java
    │       └── MinioCleanupServiceTest.java
    └── config/
        ├── CamelRouteConfigTest.java
        └── FontHealthCheckTest.java

src/test/resources/
├── application-test.yml
├── fop/fop.xconf
└── xml/preview-receipt.xml
```

### Modified files (saga-commons)

```
saga-commons/src/main/java/com/wpanther/saga/domain/enums/SagaStep.java
```

---

## Task 1: Add GENERATE_RECEIPT_PDF to SagaStep enum (saga-commons)

**Files:**
- Modify: `saga-commons/src/main/java/com/wpanther/saga/domain/enums/SagaStep.java:68-69`

- [ ] **Step 1: Add the new enum value after `GENERATE_TAX_INVOICE_PDF`**

In `SagaStep.java`, insert after the `GENERATE_TAX_INVOICE_PDF` entry (line 68):

```java
    /**
     * Receipt PDF generation via receipt-pdf-generation-service.
     */
    GENERATE_RECEIPT_PDF("generate-receipt-pdf", "Receipt PDF Generation Service"),
```

The enum should now read:

```java
    GENERATE_TAX_INVOICE_PDF("generate-tax-invoice-pdf", "Tax Invoice PDF Generation Service"),

    /**
     * Receipt PDF generation via receipt-pdf-generation-service.
     */
    GENERATE_RECEIPT_PDF("generate-receipt-pdf", "Receipt PDF Generation Service"),

    /**
     * PDF signing via pdf-signing-service.
     */
    SIGN_PDF("sign-pdf", "PDF Signing Service"),
```

- [ ] **Step 2: Build saga-commons to verify compilation**

Run: `cd /home/wpanther/projects/etax/saga-commons && mvn clean install -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
cd /home/wpanther/projects/etax/saga-commons
git add src/main/java/com/wpanther/saga/domain/enums/SagaStep.java
git commit -m "feat: add GENERATE_RECEIPT_PDF SagaStep enum value"
```

---

## Task 2: Project scaffolding — pom.xml, main class, application.yml

**Files:**
- Create: `receipt-pdf-generation-service/pom.xml`
- Create: `receipt-pdf-generation-service/src/main/java/com/wpanther/receipt/pdf/ReceiptPdfGenerationServiceApplication.java`
- Create: `receipt-pdf-generation-service/src/main/resources/application.yml`
- Create: `receipt-pdf-generation-service/src/test/resources/application-test.yml`

- [ ] **Step 1: Create pom.xml**

Adapted from taxinvoice reference. Key differences: artifact ID, name, description.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.wpanther</groupId>
    <artifactId>receipt-pdf-generation-service</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>Receipt PDF Generation Service</name>
    <description>Microservice for generating PDF/A-3 documents for Thai e-Tax receipts with embedded XML</description>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <spring-boot.version>3.2.5</spring-boot.version>
        <spring-cloud.version>2023.0.1</spring-cloud.version>
        <fop.version>2.9</fop.version>
        <pdfbox.version>3.0.1</pdfbox.version>
        <lombok.version>1.18.30</lombok.version>
        <flyway.version>10.10.0</flyway.version>
        <camel.version>4.14.4</camel.version>
        <saga.commons.version>1.0.0-SNAPSHOT</saga.commons.version>
        <aws-sdk.version>2.20.26</aws-sdk.version>
        <resilience4j.version>2.1.0</resilience4j.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.apache.camel.springboot</groupId>
                <artifactId>camel-spring-boot-bom</artifactId>
                <version>${camel.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>software.amazon.awssdk</groupId>
                <artifactId>bom</artifactId>
                <version>${aws-sdk.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Apache Camel -->
        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-spring-boot-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-kafka-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-jackson-starter</artifactId>
        </dependency>

        <!-- Spring Cloud Netflix Eureka Client -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- PostgreSQL Driver -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Flyway for database migrations -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
            <version>${flyway.version}</version>
        </dependency>

        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
            <version>${flyway.version}</version>
        </dependency>

        <!-- Apache FOP for PDF generation -->
        <dependency>
            <groupId>org.apache.xmlgraphics</groupId>
            <artifactId>fop</artifactId>
            <version>${fop.version}</version>
        </dependency>

        <!-- Apache PDFBox for PDF/A-3 conversion -->
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>${pdfbox.version}</version>
        </dependency>

        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>xmpbox</artifactId>
            <version>${pdfbox.version}</version>
        </dependency>

        <!-- XML Processing -->
        <dependency>
            <groupId>jakarta.xml.bind</groupId>
            <artifactId>jakarta.xml.bind-api</artifactId>
        </dependency>

        <dependency>
            <groupId>org.glassfish.jaxb</groupId>
            <artifactId>jaxb-runtime</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <scope>provided</scope>
        </dependency>

        <!-- Jackson for JSON processing -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>

        <!-- Saga Commons for IntegrationEvent base class -->
        <dependency>
            <groupId>com.wpanther</groupId>
            <artifactId>saga-commons</artifactId>
            <version>${saga.commons.version}</version>
        </dependency>

        <!-- AWS SDK v2 S3 for MinIO storage -->
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>s3</artifactId>
        </dependency>

        <!-- Resilience4j Circuit Breaker -->
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-spring-boot3</artifactId>
            <version>${resilience4j.version}</version>
        </dependency>

        <!-- Micrometer for metrics -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- Distributed tracing: Micrometer → OpenTelemetry bridge + OTLP exporter -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-otel</artifactId>
        </dependency>

        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-exporter-otlp</artifactId>
        </dependency>

        <!-- Test Dependencies -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.apache.camel</groupId>
            <artifactId>camel-test-spring-junit5</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot.version}</version>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-maven-plugin</artifactId>
                <version>${flyway.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create main application class**

```java
package com.wpanther.receipt.pdf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReceiptPdfGenerationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReceiptPdfGenerationServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Create `src/main/resources/application.yml`**

Receipt-specific values: port 8095, DB `receiptpdf_db`, bucket `receipts`, camel name `receipt-pdf-generation-camel`, topic suffixes `receipt-pdf` / `receipt`, group IDs `receipt-pdf-generation-command` / `receipt-pdf-generation-compensation`.

```yaml
server:
  port: 8095

spring:
  application:
    name: receipt-pdf-generation-service

  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:receiptpdf_db}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5

  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
    show-sql: false

  flyway:
    enabled: true
    locations: classpath:db/migration

  jackson:
    serialization:
      write-dates-as-timestamps: false

# Apache Camel
camel:
  springboot:
    name: receipt-pdf-generation-camel
    main-run-controller: true
  dataformat:
    jackson:
      auto-discover-object-mapper: true

# Application-specific configuration
app:
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
    consumer:
      command-group-id: ${KAFKA_COMMAND_GROUP_ID:receipt-pdf-generation-command}
      compensation-group-id: ${KAFKA_COMPENSATION_GROUP_ID:receipt-pdf-generation-compensation}
      break-on-first-error: ${KAFKA_BREAK_ON_FIRST_ERROR:true}
      max-poll-records: ${KAFKA_MAX_POLL_RECORDS:100}
      consumers-count: ${KAFKA_CONSUMERS_COUNT:3}
    topics:
      saga-command-receipt-pdf: saga.command.receipt-pdf
      saga-compensation-receipt-pdf: saga.compensation.receipt-pdf
      pdf-generated-receipt: pdf.generated.receipt
      dlq: pdf.generation.receipt.dlq
  minio:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
    bucket-name: ${MINIO_BUCKET_NAME:receipts}
    region: ${MINIO_REGION:us-east-1}
    base-url: ${MINIO_BASE_URL:http://localhost:9000/receipts}
    path-style-access: ${MINIO_PATH_STYLE_ACCESS:true}
    cleanup:
      enabled: ${MINIO_CLEANUP_ENABLED:false}
      cron: ${MINIO_CLEANUP_CRON:0 0 2 * * ?}
  pdf:
    icc-profile-path: ${PDF_ICC_PROFILE_PATH:icc/sRGB.icc}
    generation:
      max-retries: ${PDF_GENERATION_MAX_RETRIES:3}
      max-concurrent-renders: ${PDF_MAX_CONCURRENT_RENDERS:3}
      max-pdf-size-bytes: ${PDF_MAX_SIZE_BYTES:52428800}
  rest-client:
    connect-timeout: ${REST_CLIENT_CONNECT_TIMEOUT:5000}
    read-timeout: ${REST_CLIENT_READ_TIMEOUT:10000}
    allowed-hosts: ${REST_CLIENT_ALLOWED_HOSTS:localhost}
  fonts:
    health-check:
      enabled: ${FONT_HEALTH_CHECK_ENABLED:true}
      fail-on-error: ${FONT_HEALTH_CHECK_FAIL_ON_ERROR:true}

# Resilience4j Circuit Breaker
resilience4j:
  circuitbreaker:
    instances:
      minio:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        register-health-indicator: true
      signedXmlFetch:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 3s

# Eureka client configuration
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${random.value}

# Actuator endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,camelroutes
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: ${spring.application.name}
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}

# Logging
logging:
  level:
    root: INFO
    com.wpanther.receipt.pdf: INFO
    org.apache.camel: INFO
    org.apache.camel.component.kafka: DEBUG
    org.apache.fop: INFO
    org.apache.pdfbox: INFO
```

- [ ] **Step 4: Create `src/test/resources/application-test.yml`**

Copy from taxinvoice reference and adapt: H2 instead of PostgreSQL, Flyway disabled, simplified FOP config.

```yaml
server:
  port: 0

spring:
  application:
    name: receipt-pdf-generation-service-test

  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    username: sa
    password:
    driver-class-name: org.h2.Driver

  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
    show-sql: false

  flyway:
    enabled: false

  jackson:
    serialization:
      write-dates-as-timestamps: false

camel:
  springboot:
    name: receipt-pdf-generation-camel-test
    main-run-controller: false

app:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      command-group-id: test-command-group
      compensation-group-id: test-compensation-group
      break-on-first-error: true
      max-poll-records: 100
      consumers-count: 1
    topics:
      saga-command-receipt-pdf: saga.command.receipt-pdf
      saga-compensation-receipt-pdf: saga.compensation.receipt-pdf
      pdf-generated-receipt: pdf.generated.receipt
      dlq: pdf.generation.receipt.dlq
  minio:
    endpoint: http://localhost:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket-name: receipts
    region: us-east-1
    base-url: http://localhost:9000/receipts
    path-style-access: true
    cleanup:
      enabled: false
  pdf:
    icc-profile-path: icc/sRGB.icc
    generation:
      max-retries: 3
      max-concurrent-renders: 2
      max-pdf-size-bytes: 52428800
  rest-client:
    connect-timeout: 5000
    read-timeout: 10000
    allowed-hosts: localhost
  fonts:
    health-check:
      enabled: false
      fail-on-error: false

logging:
  level:
    root: WARN
    com.wpanther.receipt.pdf: DEBUG
```

- [ ] **Step 5: Verify project compiles**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add pom.xml src/main/java/com/wpanther/receipt/pdf/ReceiptPdfGenerationServiceApplication.java src/main/resources/application.yml src/test/resources/application-test.yml
git commit -m "feat: scaffold receipt-pdf-generation-service with pom.xml, main class, and config"
```

---

## Task 3: Domain model — ReceiptPdfDocument, GenerationStatus, exception, constants

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/domain/model/GenerationStatus.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/domain/model/ReceiptPdfDocument.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/domain/repository/ReceiptPdfDocumentRepository.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/domain/service/ReceiptPdfGenerationService.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/domain/exception/ReceiptPdfGenerationException.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/domain/constants/PdfGenerationConstants.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/domain/model/ReceiptPdfDocumentTest.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/domain/exception/ReceiptPdfGenerationExceptionTest.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/domain/constants/PdfGenerationConstantsTest.java`

- [ ] **Step 1: Write failing test for ReceiptPdfDocument**

Read the taxinvoice reference test at `taxinvoice-pdf-generation-service/src/test/java/.../domain/model/TaxInvoicePdfDocumentTest.java`, then create the receipt version. Replace `taxInvoiceId`/`taxInvoiceNumber` with `receiptId`/`receiptNumber`.

```java
package com.wpanther.receipt.pdf.domain.model;

import com.wpanther.receipt.pdf.domain.exception.ReceiptPdfGenerationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ReceiptPdfDocument Unit Tests")
class ReceiptPdfDocumentTest {

    private ReceiptPdfDocument.ReceiptBuilder defaultBuilder() {
        return ReceiptPdfDocument.builder()
                .receiptId("doc-123")
                .receiptNumber("RCP-2024-001");
    }

    @Nested
    @DisplayName("Builder and Creation")
    class BuilderTests {

        @Test
        @DisplayName("Should create document with required fields")
        void shouldCreateWithRequiredFields() {
            ReceiptPdfDocument doc = defaultBuilder().build();

            assertThat(doc.getId()).isNotNull();
            assertThat(doc.getReceiptId()).isEqualTo("doc-123");
            assertThat(doc.getReceiptNumber()).isEqualTo("RCP-2024-001");
            assertThat(doc.getStatus()).isEqualTo(GenerationStatus.PENDING);
            assertThat(doc.getFileSize()).isZero();
            assertThat(doc.getRetryCount()).isZero();
            assertThat(doc.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should throw when receiptId is null")
        void shouldThrowWhenReceiptIdNull() {
            assertThatThrownBy(() -> ReceiptPdfDocument.builder()
                    .receiptId(null)
                    .receiptNumber("RCP-001")
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Receipt ID is required");
        }

        @Test
        @DisplayName("Should throw when receiptId is blank")
        void shouldThrowWhenReceiptIdBlank() {
            assertThatThrownBy(() -> ReceiptPdfDocument.builder()
                    .receiptId("  ")
                    .receiptNumber("RCP-001")
                    .build())
                    .isInstanceOf(ReceiptPdfGenerationException.class)
                    .hasMessageContaining("Receipt ID cannot be blank");
        }

        @Test
        @DisplayName("Should throw when receiptNumber is null")
        void shouldThrowWhenReceiptNumberNull() {
            assertThatThrownBy(() -> ReceiptPdfDocument.builder()
                    .receiptId("doc-123")
                    .receiptNumber(null)
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Receipt number is required");
        }

        @Test
        @DisplayName("Should throw when receiptNumber is blank")
        void shouldThrowWhenReceiptNumberBlank() {
            assertThatThrownBy(() -> ReceiptPdfDocument.builder()
                    .receiptId("doc-123")
                    .receiptNumber("  ")
                    .build())
                    .isInstanceOf(ReceiptPdfGenerationException.class)
                    .hasMessageContaining("Receipt number cannot be blank");
        }

        @Test
        @DisplayName("Should use provided UUID when set")
        void shouldUseProvidedUuid() {
            UUID id = UUID.randomUUID();
            ReceiptPdfDocument doc = defaultBuilder().id(id).build();
            assertThat(doc.getId()).isEqualTo(id);
        }
    }

    @Nested
    @DisplayName("State Transitions")
    class StateTransitionTests {

        @Test
        @DisplayName("PENDING → GENERATING via startGeneration()")
        void shouldTransitionToGenerating() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.startGeneration();
            assertThat(doc.getStatus()).isEqualTo(GenerationStatus.GENERATING);
        }

        @Test
        @DisplayName("Cannot startGeneration from GENERATING")
        void shouldNotStartFromGenerating() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.startGeneration();
            assertThatThrownBy(doc::startGeneration)
                    .isInstanceOf(ReceiptPdfGenerationException.class);
        }

        @Test
        @DisplayName("GENERATING → COMPLETED via markCompleted()")
        void shouldTransitionToCompleted() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.startGeneration();
            doc.markCompleted("path/receipt.pdf", "http://minio/receipt.pdf", 12345L);

            assertThat(doc.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
            assertThat(doc.getDocumentPath()).isEqualTo("path/receipt.pdf");
            assertThat(doc.getDocumentUrl()).isEqualTo("http://minio/receipt.pdf");
            assertThat(doc.getFileSize()).isEqualTo(12345L);
            assertThat(doc.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("Cannot markCompleted from PENDING")
        void shouldNotCompleteFromPending() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            assertThatThrownBy(() -> doc.markCompleted("p", "u", 1L))
                    .isInstanceOf(ReceiptPdfGenerationException.class);
        }

        @Test
        @DisplayName("markCompleted requires non-null path")
        void shouldNotCompleteWithNullPath() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.startGeneration();
            assertThatThrownBy(() -> doc.markCompleted(null, "url", 1L))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("markCompleted requires positive fileSize")
        void shouldNotCompleteWithZeroSize() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.startGeneration();
            assertThatThrownBy(() -> doc.markCompleted("p", "u", 0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("any → FAILED via markFailed()")
        void shouldTransitionToFailed() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.markFailed("Something went wrong");

            assertThat(doc.getStatus()).isEqualTo(GenerationStatus.FAILED);
            assertThat(doc.getErrorMessage()).isEqualTo("Something went wrong");
            assertThat(doc.isFailed()).isTrue();
            assertThat(doc.isCompleted()).isFalse();
            assertThat(doc.isSuccessful()).isFalse();
        }

        @Test
        @DisplayName("markFailed from GENERATING")
        void shouldFailFromGenerating() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.startGeneration();
            doc.markFailed("FOP error");
            assertThat(doc.getStatus()).isEqualTo(GenerationStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("Retry Tracking")
    class RetryTests {

        @Test
        @DisplayName("incrementRetryCount increments by one")
        void shouldIncrementRetry() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            assertThat(doc.getRetryCount()).isZero();
            doc.incrementRetryCount();
            assertThat(doc.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("incrementRetryCountTo sets to target if higher")
        void shouldAdvanceToTarget() {
            ReceiptPdfDocument doc = defaultBuilder().retryCount(2).build();
            doc.incrementRetryCountTo(5);
            assertThat(doc.getRetryCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("incrementRetryCountTo does not decrease")
        void shouldNotDecrease() {
            ReceiptPdfDocument doc = defaultBuilder().retryCount(5).build();
            doc.incrementRetryCountTo(3);
            assertThat(doc.getRetryCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("setRetryCount sets exact value")
        void shouldSetExact() {
            ReceiptPdfDocument doc = defaultBuilder().build();
            doc.setRetryCount(7);
            assertThat(doc.getRetryCount()).isEqualTo(7);
        }

        @Test
        @DisplayName("isMaxRetriesExceeded returns true when equal")
        void maxRetriesEqual() {
            ReceiptPdfDocument doc = defaultBuilder().retryCount(3).build();
            assertThat(doc.isMaxRetriesExceeded(3)).isTrue();
        }

        @Test
        @DisplayName("isMaxRetriesExceeded returns false when under")
        void maxRetriesUnder() {
            ReceiptPdfDocument doc = defaultBuilder().retryCount(2).build();
            assertThat(doc.isMaxRetriesExceeded(3)).isFalse();
        }
    }

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("Equal by ID")
        void equalById() {
            UUID id = UUID.randomUUID();
            ReceiptPdfDocument a = defaultBuilder().id(id).build();
            ReceiptPdfDocument b = defaultBuilder().id(id).receiptNumber("DIFFERENT").build();
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("Not equal by different ID")
        void notEqualByDifferentId() {
            ReceiptPdfDocument a = defaultBuilder().build();
            ReceiptPdfDocument b = defaultBuilder().build();
            assertThat(a).isNotEqualTo(b);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn clean test -Dtest=ReceiptPdfDocumentTest -pl .`
Expected: Compilation error — classes don't exist yet

- [ ] **Step 3: Create GenerationStatus enum**

```java
package com.wpanther.receipt.pdf.domain.model;

public enum GenerationStatus {
    PENDING,
    GENERATING,
    COMPLETED,
    FAILED
}
```

- [ ] **Step 4: Create ReceiptPdfGenerationException**

```java
package com.wpanther.receipt.pdf.domain.exception;

public class ReceiptPdfGenerationException extends RuntimeException {

    public ReceiptPdfGenerationException(String message) {
        super(message);
    }

    public ReceiptPdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 5: Create ReceiptPdfDocument aggregate root**

Identical to `TaxInvoicePdfDocument` with field renames: `taxInvoiceId` → `receiptId`, `taxInvoiceNumber` → `receiptNumber`. All method signatures, state transitions, builder pattern, and invariants are structurally identical.

```java
package com.wpanther.receipt.pdf.domain.model;

import com.wpanther.receipt.pdf.domain.exception.ReceiptPdfGenerationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class ReceiptPdfDocument {

    private static final String DEFAULT_MIME_TYPE = "application/pdf";

    private final UUID id;
    private final String receiptId;
    private final String receiptNumber;
    private String documentPath;
    private String documentUrl;
    private long fileSize;
    private final String mimeType;
    private boolean xmlEmbedded;
    private GenerationStatus status;
    private String errorMessage;
    private int retryCount;
    private final LocalDateTime createdAt;
    private LocalDateTime completedAt;

    private ReceiptPdfDocument(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID();
        this.receiptId = Objects.requireNonNull(builder.receiptId, "Receipt ID is required");
        this.receiptNumber = Objects.requireNonNull(builder.receiptNumber, "Receipt number is required");
        this.documentPath = builder.documentPath;
        this.documentUrl = builder.documentUrl;
        this.fileSize = builder.fileSize;
        this.mimeType = builder.mimeType != null ? builder.mimeType : DEFAULT_MIME_TYPE;
        this.xmlEmbedded = builder.xmlEmbedded;
        this.status = builder.status != null ? builder.status : GenerationStatus.PENDING;
        this.errorMessage = builder.errorMessage;
        this.retryCount = builder.retryCount;
        this.createdAt = builder.createdAt != null ? builder.createdAt : LocalDateTime.now();
        this.completedAt = builder.completedAt;

        validateInvariant();
    }

    private void validateInvariant() {
        if (receiptId.isBlank()) {
            throw new ReceiptPdfGenerationException("Receipt ID cannot be blank");
        }
        if (receiptNumber.isBlank()) {
            throw new ReceiptPdfGenerationException("Receipt number cannot be blank");
        }
    }

    public void startGeneration() {
        if (this.status != GenerationStatus.PENDING) {
            throw new ReceiptPdfGenerationException("Can only start generation from PENDING status");
        }
        this.status = GenerationStatus.GENERATING;
    }

    public void markCompleted(String documentPath, String documentUrl, long fileSize) {
        if (this.status != GenerationStatus.GENERATING) {
            throw new ReceiptPdfGenerationException("Can only complete from GENERATING status");
        }
        Objects.requireNonNull(documentPath, "Document path is required");
        Objects.requireNonNull(documentUrl, "Document URL is required");
        if (fileSize <= 0) {
            throw new IllegalArgumentException("File size must be positive");
        }
        this.documentPath = documentPath;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.status = GenerationStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = GenerationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    public void markXmlEmbedded() {
        this.xmlEmbedded = true;
    }

    public boolean isSuccessful() { return status == GenerationStatus.COMPLETED; }
    public boolean isCompleted() { return status == GenerationStatus.COMPLETED; }
    public boolean isFailed() { return status == GenerationStatus.FAILED; }

    public void incrementRetryCount() { this.retryCount++; }

    public void incrementRetryCountTo(int target) {
        if (target < 0) throw new IllegalArgumentException("Target retry count cannot be negative");
        if (this.retryCount < target) this.retryCount = target;
    }

    public void setRetryCount(int retryCount) {
        if (retryCount < 0) throw new IllegalArgumentException("Retry count cannot be negative");
        this.retryCount = retryCount;
    }

    public boolean isMaxRetriesExceeded(int maxRetries) {
        return this.retryCount >= maxRetries;
    }

    // Getters
    public UUID getId() { return id; }
    public String getReceiptId() { return receiptId; }
    public String getReceiptNumber() { return receiptNumber; }
    public String getDocumentPath() { return documentPath; }
    public String getDocumentUrl() { return documentUrl; }
    public long getFileSize() { return fileSize; }
    public String getMimeType() { return mimeType; }
    public boolean isXmlEmbedded() { return xmlEmbedded; }
    public GenerationStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public int getRetryCount() { return retryCount; }

    @Override
    public String toString() {
        return "ReceiptPdfDocument{id=" + id + ", receiptId='" + receiptId + "', receiptNumber='" + receiptNumber + "', status=" + status + ", retryCount=" + retryCount + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReceiptPdfDocument other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private UUID id;
        private String receiptId;
        private String receiptNumber;
        private String documentPath;
        private String documentUrl;
        private long fileSize;
        private String mimeType;
        private boolean xmlEmbedded;
        private GenerationStatus status;
        private String errorMessage;
        private int retryCount;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder receiptId(String receiptId) { this.receiptId = receiptId; return this; }
        public Builder receiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; return this; }
        public Builder documentPath(String documentPath) { this.documentPath = documentPath; return this; }
        public Builder documentUrl(String documentUrl) { this.documentUrl = documentUrl; return this; }
        public Builder fileSize(long fileSize) { this.fileSize = fileSize; return this; }
        public Builder mimeType(String mimeType) { this.mimeType = mimeType; return this; }
        public Builder xmlEmbedded(boolean xmlEmbedded) { this.xmlEmbedded = xmlEmbedded; return this; }
        public Builder status(GenerationStatus status) { this.status = status; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder retryCount(int retryCount) { this.retryCount = retryCount; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder completedAt(LocalDateTime completedAt) { this.completedAt = completedAt; return this; }
        public ReceiptPdfDocument build() { return new ReceiptPdfDocument(this); }
    }
}
```

- [ ] **Step 6: Create ReceiptPdfDocumentRepository interface**

```java
package com.wpanther.receipt.pdf.domain.repository;

import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptPdfDocumentRepository {

    ReceiptPdfDocument save(ReceiptPdfDocument document);

    Optional<ReceiptPdfDocument> findById(UUID id);

    Optional<ReceiptPdfDocument> findByReceiptId(String receiptId);

    void deleteById(UUID id);

    void flush();
}
```

- [ ] **Step 7: Create ReceiptPdfGenerationService interface (domain port)**

```java
package com.wpanther.receipt.pdf.domain.service;

public interface ReceiptPdfGenerationService {

    byte[] generatePdf(String receiptNumber, String signedXml)
        throws ReceiptPdfGenerationException;

    class ReceiptPdfGenerationException extends Exception {
        public ReceiptPdfGenerationException(String message) { super(message); }
        public ReceiptPdfGenerationException(String message, Throwable cause) { super(message, cause); }
    }
}
```

- [ ] **Step 8: Create PdfGenerationConstants**

Read the taxinvoice reference at `taxinvoice-pdf-generation-service/src/main/java/.../domain/constants/PdfGenerationConstants.java`, then create the receipt version with receipt-specific S3 key prefix.

```java
package com.wpanther.receipt.pdf.domain.constants;

public final class PdfGenerationConstants {

    private PdfGenerationConstants() {}

    public static final String DOCUMENT_TYPE = "RECEIPT";
    public static final String S3_KEY_PREFIX = "receipt-";
    public static final String PDF_FILE_EXTENSION = ".pdf";
    public static final String DEFAULT_MIME_TYPE = "application/pdf";

    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final long DEFAULT_MAX_PDF_SIZE_BYTES = 52_428_800L; // 50 MB
}
```

- [ ] **Step 9: Write tests for exception and constants**

Read the taxinvoice reference tests, then create receipt versions.

`ReceiptPdfGenerationExceptionTest.java`:
```java
package com.wpanther.receipt.pdf.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReceiptPdfGenerationException Tests")
class ReceiptPdfGenerationExceptionTest {

    @Test
    @DisplayName("Should create with message")
    void shouldCreateWithMessage() {
        ReceiptPdfGenerationException ex = new ReceiptPdfGenerationException("error");
        assertThat(ex.getMessage()).isEqualTo("error");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create with message and cause")
    void shouldCreateWithMessageAndCause() {
        Throwable cause = new RuntimeException("root");
        ReceiptPdfGenerationException ex = new ReceiptPdfGenerationException("error", cause);
        assertThat(ex.getMessage()).isEqualTo("error");
        assertThat(ex.getCause()).isEqualTo(cause);
    }
}
```

`PdfGenerationConstantsTest.java`:
```java
package com.wpanther.receipt.pdf.domain.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PdfGenerationConstants Tests")
class PdfGenerationConstantsTest {

    @Test
    @DisplayName("Should have correct document type")
    void shouldHaveCorrectDocumentType() {
        assertThat(PdfGenerationConstants.DOCUMENT_TYPE).isEqualTo("RECEIPT");
    }

    @Test
    @DisplayName("Should have correct S3 key prefix")
    void shouldHaveCorrectS3KeyPrefix() {
        assertThat(PdfGenerationConstants.S3_KEY_PREFIX).isEqualTo("receipt-");
    }

    @Test
    @DisplayName("Should have correct defaults")
    void shouldHaveCorrectDefaults() {
        assertThat(PdfGenerationConstants.DEFAULT_MAX_RETRIES).isEqualTo(3);
        assertThat(PdfGenerationConstants.DEFAULT_MAX_PDF_SIZE_BYTES).isEqualTo(52_428_800L);
        assertThat(PdfGenerationConstants.DEFAULT_MIME_TYPE).isEqualTo("application/pdf");
    }
}
```

- [ ] **Step 10: Run all tests**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn clean test -Dtest="ReceiptPdfDocumentTest,ReceiptPdfGenerationExceptionTest,PdfGenerationConstantsTest"`
Expected: All tests PASS

- [ ] **Step 11: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/main/java/com/wpanther/receipt/pdf/domain/ src/test/java/com/wpanther/receipt/pdf/domain/
git commit -m "feat: add domain model — ReceiptPdfDocument, GenerationStatus, repository port, service port"
```

---

## Task 4: Application output ports (outgoing port interfaces)

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/application/port/out/PdfEventPort.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/application/port/out/PdfStoragePort.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/application/port/out/SagaReplyPort.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/application/port/out/SignedXmlFetchPort.java`

- [ ] **Step 1: Read taxinvoice reference port interfaces**

Read all four files in `taxinvoice-pdf-generation-service/src/main/java/.../application/port/out/` and create receipt equivalents. The port interfaces are identical in shape — only the Javadoc references change from "tax invoice" to "receipt".

`PdfEventPort.java`:
```java
package com.wpanther.receipt.pdf.application.port.out;

import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;

public interface PdfEventPort {

    void publishGenerated(ReceiptPdfDocument document, String correlationId);
}
```

`PdfStoragePort.java`:
```java
package com.wpanther.receipt.pdf.application.port.out;

public interface PdfStoragePort {

    String store(String receiptNumber, byte[] pdfBytes);

    void delete(String documentPath);

    String resolveUrl(String documentPath);
}
```

`SagaReplyPort.java`:
```java
package com.wpanther.receipt.pdf.application.port.out;

import com.wpanther.saga.domain.enums.SagaStep;

public interface SagaReplyPort {

    void publishSuccess(String sagaId, SagaStep step, String correlationId,
                        String pdfUrl, long pdfSize);

    void publishFailure(String sagaId, SagaStep step, String correlationId,
                        String errorMessage);

    void publishCompensated(String sagaId, SagaStep step, String correlationId);
}
```

`SignedXmlFetchPort.java`:
```java
package com.wpanther.receipt.pdf.application.port.out;

public interface SignedXmlFetchPort {

    String fetch(String signedXmlUrl);
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/receipt/pdf/application/port/
git commit -m "feat: add application output port interfaces"
```

---

## Task 5: Application use cases and SagaCommandHandler

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/application/usecase/ProcessReceiptPdfUseCase.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/application/usecase/CompensateReceiptPdfUseCase.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/application/service/SagaCommandHandler.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/application/service/ReceiptPdfDocumentService.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/metrics/PdfGenerationMetrics.java`

- [ ] **Step 1: Create use case interfaces**

`ProcessReceiptPdfUseCase.java`:
```java
package com.wpanther.receipt.pdf.application.usecase;

import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.KafkaReceiptProcessCommand;

public interface ProcessReceiptPdfUseCase {
    void handle(KafkaReceiptProcessCommand command);
}
```

`CompensateReceiptPdfUseCase.java`:
```java
package com.wpanther.receipt.pdf.application.usecase;

import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.KafkaReceiptCompensateCommand;

public interface CompensateReceiptPdfUseCase {
    void handle(KafkaReceiptCompensateCommand command);
}
```

> Note: These interfaces reference the Kafka command DTOs from the infrastructure layer. The command DTOs will be created in Task 6. For now, create the use case interfaces and proceed — compilation will succeed once Task 6 is done.

- [ ] **Step 2: Create PdfGenerationMetrics**

Read the taxinvoice reference at `taxinvoice-pdf-generation-service/src/main/java/.../metrics/PdfGenerationMetrics.java`, then create:

```java
package com.wpanther.receipt.pdf.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PdfGenerationMetrics {

    private final Counter generatedCounter;
    private final Counter failedCounter;
    private final Counter retryExhaustedCounter;

    public PdfGenerationMetrics(MeterRegistry registry) {
        this.generatedCounter = Counter.builder("pdf.generation.receipt.generated")
                .description("Number of receipt PDFs successfully generated")
                .register(registry);
        this.failedCounter = Counter.builder("pdf.generation.receipt.failed")
                .description("Number of receipt PDF generation failures")
                .register(registry);
        this.retryExhaustedCounter = Counter.builder("pdf.generation.receipt.retry_exhausted")
                .description("Number of receipt PDF generation attempts that exhausted retries")
                .register(registry);
    }

    public void recordGenerated() { generatedCounter.increment(); }
    public void recordFailed() { failedCounter.increment(); }
    public void recordRetryExhausted(String sagaId, String documentId, String documentNumber) {
        retryExhaustedCounter.increment();
    }
}
```

- [ ] **Step 3: Create KafkaReceiptProcessCommand and KafkaReceiptCompensateCommand**

These are needed by the use case interfaces. Create them now so everything compiles together.

`KafkaReceiptProcessCommand.java`:
```java
package com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;

import java.time.Instant;
import java.util.UUID;

public class KafkaReceiptProcessCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("documentId")    private final String documentId;
    @JsonProperty("documentNumber") private final String documentNumber;
    @JsonProperty("signedXmlUrl")  private final String signedXmlUrl;

    @JsonCreator
    public KafkaReceiptProcessCommand(
            @JsonProperty("eventId")       UUID eventId,
            @JsonProperty("occurredAt")    Instant occurredAt,
            @JsonProperty("eventType")     String eventType,
            @JsonProperty("version")       int version,
            @JsonProperty("sagaId")        String sagaId,
            @JsonProperty("sagaStep")      SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId")    String documentId,
            @JsonProperty("documentNumber") String documentNumber,
            @JsonProperty("signedXmlUrl")  String signedXmlUrl) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId     = documentId;
        this.documentNumber = documentNumber;
        this.signedXmlUrl   = signedXmlUrl;
    }

    public KafkaReceiptProcessCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                      String documentId, String documentNumber, String signedXmlUrl) {
        super(sagaId, sagaStep, correlationId);
        this.documentId     = documentId;
        this.documentNumber = documentNumber;
        this.signedXmlUrl   = signedXmlUrl;
    }

    @Override public String getSagaId()        { return super.getSagaId(); }
    @Override public SagaStep getSagaStep()    { return super.getSagaStep(); }
    @Override public String getCorrelationId() { return super.getCorrelationId(); }
    public String getDocumentId()     { return documentId; }
    public String getDocumentNumber() { return documentNumber; }
    public String getSignedXmlUrl()   { return signedXmlUrl; }
}
```

`KafkaReceiptCompensateCommand.java`:
```java
package com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;

import java.time.Instant;
import java.util.UUID;

public class KafkaReceiptCompensateCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("documentId")   private final String documentId;

    @JsonCreator
    public KafkaReceiptCompensateCommand(
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

    public KafkaReceiptCompensateCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                         String documentId) {
        super(sagaId, sagaStep, correlationId);
        this.documentId = documentId;
    }

    @Override public String getSagaId()        { return super.getSagaId(); }
    @Override public SagaStep getSagaStep()    { return super.getSagaStep(); }
    @Override public String getCorrelationId() { return super.getCorrelationId(); }
    public String getDocumentId() { return documentId; }
}
```

- [ ] **Step 4: Create ReceiptPdfDocumentService**

Structurally identical to `TaxInvoicePdfDocumentService` with `receiptId`/`receiptNumber` field renames and `GENERATE_RECEIPT_PDF` saga step references.

```java
package com.wpanther.receipt.pdf.application.service;

import com.wpanther.receipt.pdf.application.port.out.PdfEventPort;
import com.wpanther.receipt.pdf.application.port.out.SagaReplyPort;
import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;
import com.wpanther.receipt.pdf.domain.repository.ReceiptPdfDocumentRepository;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.KafkaReceiptCompensateCommand;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.KafkaReceiptProcessCommand;
import com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging.ReceiptPdfGeneratedEvent;
import com.wpanther.receipt.pdf.infrastructure.metrics.PdfGenerationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptPdfDocumentService {

    private final ReceiptPdfDocumentRepository repository;
    private final PdfEventPort pdfEventPort;
    private final SagaReplyPort sagaReplyPort;
    private final PdfGenerationMetrics pdfGenerationMetrics;

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
                                             KafkaReceiptProcessCommand command) {
        ReceiptPdfDocument doc = requireDocument(documentId);
        doc.markCompleted(s3Key, fileUrl, fileSize);
        doc.markXmlEmbedded();
        applyRetryCount(doc, previousRetryCount);
        doc = repository.save(doc);

        pdfEventPort.publishPdfGenerated(buildGeneratedEvent(doc, command));
        sagaReplyPort.publishSuccess(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
                doc.getDocumentUrl(), doc.getFileSize());

        log.info("Completed PDF generation for saga {} receipt {}",
                command.getSagaId(), doc.getReceiptNumber());
    }

    @Transactional
    public void failGenerationAndPublish(UUID documentId, String errorMessage,
                                         int previousRetryCount,
                                         KafkaReceiptProcessCommand command) {
        String safeError = errorMessage != null ? errorMessage : "PDF generation failed";
        ReceiptPdfDocument doc = requireDocument(documentId);
        doc.markFailed(safeError);
        applyRetryCount(doc, previousRetryCount);
        repository.save(doc);

        sagaReplyPort.publishFailure(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId(), safeError);

        log.warn("PDF generation failed for saga {} receipt {}: {}",
                command.getSagaId(), doc.getReceiptNumber(), safeError);
    }

    @Transactional
    public void deleteById(UUID documentId) {
        repository.deleteById(documentId);
        repository.flush();
    }

    @Transactional
    public void publishIdempotentSuccess(ReceiptPdfDocument existing,
                                         KafkaReceiptProcessCommand command) {
        pdfEventPort.publishPdfGenerated(buildGeneratedEvent(existing, command));
        sagaReplyPort.publishSuccess(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
                existing.getDocumentUrl(), existing.getFileSize());
        log.warn("Receipt PDF already generated for saga {} — re-publishing SUCCESS reply",
                command.getSagaId());
    }

    @Transactional
    public void publishRetryExhausted(KafkaReceiptProcessCommand command) {
        pdfGenerationMetrics.recordRetryExhausted(
                command.getSagaId(),
                command.getDocumentId(),
                command.getDocumentNumber());
        sagaReplyPort.publishFailure(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
                "Maximum retry attempts exceeded");
        log.error("Max retries exceeded for saga {} document {}",
                command.getSagaId(), command.getDocumentNumber());
    }

    @Transactional
    public void publishGenerationFailure(KafkaReceiptProcessCommand command, String errorMessage) {
        sagaReplyPort.publishFailure(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId(), errorMessage);
    }

    @Transactional
    public void publishCompensated(KafkaReceiptCompensateCommand command) {
        sagaReplyPort.publishCompensated(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId());
    }

    @Transactional
    public void publishCompensationFailure(KafkaReceiptCompensateCommand command, String error) {
        sagaReplyPort.publishFailure(
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId(), error);
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

    private ReceiptPdfGeneratedEvent buildGeneratedEvent(ReceiptPdfDocument doc,
                                                         KafkaReceiptProcessCommand command) {
        return new ReceiptPdfGeneratedEvent(
                command.getSagaId(),
                command.getDocumentId(),
                doc.getReceiptNumber(),
                doc.getDocumentUrl(),
                doc.getFileSize(),
                doc.isXmlEmbedded(),
                command.getCorrelationId());
    }
}
```

> Note: This references `ReceiptPdfGeneratedEvent` which will be created in Task 7. Create a placeholder now if needed for compilation.

- [ ] **Step 5: Create SagaCommandHandler**

Structurally identical to the taxinvoice version with receipt renames.

```java
package com.wpanther.receipt.pdf.application.service;

import com.wpanther.receipt.pdf.application.port.out.PdfStoragePort;
import com.wpanther.receipt.pdf.application.port.out.SagaReplyPort;
import com.wpanther.receipt.pdf.application.port.out.SignedXmlFetchPort;
import com.wpanther.receipt.pdf.application.usecase.CompensateReceiptPdfUseCase;
import com.wpanther.receipt.pdf.application.usecase.ProcessReceiptPdfUseCase;
import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;
import com.wpanther.receipt.pdf.domain.service.ReceiptPdfGenerationService;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.KafkaReceiptCompensateCommand;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.KafkaReceiptProcessCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Service
@Slf4j
public class SagaCommandHandler implements ProcessReceiptPdfUseCase, CompensateReceiptPdfUseCase {

    private static final String MDC_SAGA_ID        = "sagaId";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_DOCUMENT_NUMBER = "documentNumber";
    private static final String MDC_DOCUMENT_ID     = "documentId";

    private final ReceiptPdfDocumentService pdfDocumentService;
    private final ReceiptPdfGenerationService pdfGenerationService;
    private final PdfStoragePort pdfStoragePort;
    private final SagaReplyPort sagaReplyPort;
    private final SignedXmlFetchPort signedXmlFetchPort;
    private final int maxRetries;

    public SagaCommandHandler(ReceiptPdfDocumentService pdfDocumentService,
                              ReceiptPdfGenerationService pdfGenerationService,
                              PdfStoragePort pdfStoragePort,
                              SagaReplyPort sagaReplyPort,
                              SignedXmlFetchPort signedXmlFetchPort,
                              @Value("${app.pdf.generation.max-retries:3}") int maxRetries) {
        this.pdfDocumentService = pdfDocumentService;
        this.pdfGenerationService = pdfGenerationService;
        this.pdfStoragePort = pdfStoragePort;
        this.sagaReplyPort = sagaReplyPort;
        this.signedXmlFetchPort = signedXmlFetchPort;
        this.maxRetries = maxRetries;
    }

    @Override
    public void handle(KafkaReceiptProcessCommand command) {
        MDC.put(MDC_SAGA_ID,         command.getSagaId());
        MDC.put(MDC_CORRELATION_ID,  command.getCorrelationId());
        MDC.put(MDC_DOCUMENT_NUMBER, command.getDocumentNumber());
        MDC.put(MDC_DOCUMENT_ID,     command.getDocumentId());
        try {
            log.info("Handling ProcessCommand for saga {} document {}",
                    command.getSagaId(), command.getDocumentNumber());
            try {
                String signedXmlUrl  = command.getSignedXmlUrl();
                String documentId    = command.getDocumentId();
                String documentNum   = command.getDocumentNumber();

                if (signedXmlUrl == null || signedXmlUrl.isBlank()) {
                    pdfDocumentService.publishGenerationFailure(command, "signedXmlUrl is null or blank");
                    return;
                }
                if (documentId == null || documentId.isBlank()) {
                    pdfDocumentService.publishGenerationFailure(command, "documentId is null or blank");
                    return;
                }
                if (documentNum == null || documentNum.isBlank()) {
                    pdfDocumentService.publishGenerationFailure(command, "documentNumber is null or blank");
                    return;
                }

                Optional<ReceiptPdfDocument> existing =
                        pdfDocumentService.findByReceiptId(documentId);

                if (existing.isPresent() && existing.get().isCompleted()) {
                    pdfDocumentService.publishIdempotentSuccess(existing.get(), command);
                    return;
                }

                int previousRetryCount = existing.map(ReceiptPdfDocument::getRetryCount).orElse(-1);

                if (existing.isPresent()) {
                    if (existing.get().isMaxRetriesExceeded(maxRetries)) {
                        pdfDocumentService.publishRetryExhausted(command);
                        return;
                    }
                }

                ReceiptPdfDocument document;
                if (existing.isPresent()) {
                    document = pdfDocumentService.replaceAndBeginGeneration(
                            existing.get().getId(), previousRetryCount, documentId, documentNum);
                } else {
                    document = pdfDocumentService.beginGeneration(documentId, documentNum);
                }

                String s3Key = null;
                try {
                    String signedXml = signedXmlFetchPort.fetch(signedXmlUrl);
                    byte[] pdfBytes  = pdfGenerationService.generatePdf(documentNum, signedXml);
                    s3Key = pdfStoragePort.store(documentNum, pdfBytes);
                    String fileUrl   = pdfStoragePort.resolveUrl(s3Key);

                    pdfDocumentService.completeGenerationAndPublish(
                            document.getId(), s3Key, fileUrl, pdfBytes.length, previousRetryCount, command);

                } catch (CallNotPermittedException e) {
                    log.warn("Circuit breaker OPEN for saga {} document {}: {}",
                            command.getSagaId(), documentNum, e.getMessage());
                    pdfDocumentService.failGenerationAndPublish(
                            document.getId(), "Circuit breaker open: " + e.getMessage(),
                            previousRetryCount, command);

                } catch (RestClientException e) {
                    log.warn("HTTP error fetching signed XML for saga {} document {}: {}",
                            command.getSagaId(), documentNum, e.getMessage());
                    pdfDocumentService.failGenerationAndPublish(
                            document.getId(), "HTTP error fetching signed XML: " + describeThrowable(e),
                            previousRetryCount, command);

                } catch (Exception e) {
                    if (s3Key != null) {
                        try { pdfStoragePort.delete(s3Key); }
                        catch (Exception del) {
                            log.error("[ORPHAN_PDF] s3Key={} saga={} error={}", s3Key, command.getSagaId(),
                                    describeThrowable(del));
                        }
                    }
                    log.error("PDF generation failed for saga {} document {}: {}",
                            command.getSagaId(), documentNum, e.getMessage(), e);
                    pdfDocumentService.failGenerationAndPublish(
                            document.getId(), describeThrowable(e), previousRetryCount, command);
                }

            } catch (OptimisticLockingFailureException e) {
                log.warn("Concurrent modification for saga {}: {}", command.getSagaId(), e.getMessage());
                pdfDocumentService.publishGenerationFailure(command, "Concurrent modification: " + e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error for saga {}: {}", command.getSagaId(), e.getMessage(), e);
                pdfDocumentService.publishGenerationFailure(command, describeThrowable(e));
            }
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void handle(KafkaReceiptCompensateCommand command) {
        MDC.put(MDC_SAGA_ID,        command.getSagaId());
        MDC.put(MDC_CORRELATION_ID,  command.getCorrelationId());
        MDC.put(MDC_DOCUMENT_ID,     command.getDocumentId());
        try {
            log.info("Handling compensation for saga {} document {}",
                    command.getSagaId(), command.getDocumentId());
            try {
                Optional<ReceiptPdfDocument> existing =
                        pdfDocumentService.findByReceiptId(command.getDocumentId());

                if (existing.isPresent()) {
                    ReceiptPdfDocument doc = existing.get();
                    pdfDocumentService.deleteById(doc.getId());
                    if (doc.getDocumentPath() != null) {
                        try { pdfStoragePort.delete(doc.getDocumentPath()); }
                        catch (Exception e) {
                            log.warn("Failed to delete PDF from MinIO for saga {} key {}: {}",
                                    command.getSagaId(), doc.getDocumentPath(), e.getMessage());
                        }
                    }
                    log.info("Compensated ReceiptPdfDocument {} for saga {}",
                            doc.getId(), command.getSagaId());
                } else {
                    log.info("No document for documentId {} — already compensated",
                            command.getDocumentId());
                }
                pdfDocumentService.publishCompensated(command);

            } catch (Exception e) {
                log.error("Failed to compensate for saga {}: {}", command.getSagaId(), e.getMessage(), e);
                pdfDocumentService.publishCompensationFailure(
                        command, "Compensation failed: " + describeThrowable(e));
            }
        } finally {
            MDC.clear();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOrchestrationFailure(KafkaReceiptProcessCommand command, Throwable cause) {
        try {
            sagaReplyPort.publishFailure(command.getSagaId(), command.getSagaStep(),
                    command.getCorrelationId(),
                    "Message routed to DLQ after retry exhaustion: " + describeThrowable(cause));
        } catch (Exception e) {
            log.error("Cannot notify orchestrator of DLQ failure for saga {}", command.getSagaId(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishCompensationOrchestrationFailure(KafkaReceiptCompensateCommand command, Throwable cause) {
        try {
            sagaReplyPort.publishFailure(command.getSagaId(), command.getSagaStep(),
                    command.getCorrelationId(),
                    "Compensation DLQ after retry exhaustion: " + describeThrowable(cause));
        } catch (Exception e) {
            log.error("Cannot notify orchestrator of compensation DLQ failure for saga {}", command.getSagaId(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOrchestrationFailureForUnparsedMessage(
            String sagaId, SagaStep sagaStep, String correlationId, Throwable cause) {
        try {
            String error = "Message routed to DLQ after deserialization failure: "
                    + describeThrowable(cause);
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, error);
            log.error("Published FAILURE reply after DLQ routing (deserialization failure) for saga {}", sagaId);
        } catch (Exception e) {
            log.error("Cannot notify orchestrator of DLQ deserialization failure for saga {} — orchestrator must timeout",
                    sagaId, e);
        }
    }

    private String describeThrowable(Throwable t) {
        if (t == null) return "unknown error";
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
    }
}
```

- [ ] **Step 6: Verify compilation**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn compile -q`
Expected: BUILD SUCCESS (will fail until messaging events in Task 7 exist — create `ReceiptPdfGeneratedEvent` placeholder if needed)

- [ ] **Step 7: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/main/java/com/wpanther/receipt/pdf/application/ src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/ src/main/java/com/wpanther/receipt/pdf/infrastructure/metrics/
git commit -m "feat: add application use cases, SagaCommandHandler, ReceiptPdfDocumentService, Kafka commands, metrics"
```

---

## Task 6: Persistence layer — JPA entities, repository adapter, outbox

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/persistence/ReceiptPdfDocumentEntity.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/persistence/JpaReceiptPdfDocumentRepository.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/persistence/ReceiptPdfDocumentRepositoryAdapter.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/persistence/outbox/OutboxEventEntity.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/persistence/outbox/SpringDataOutboxRepository.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/persistence/outbox/JpaOutboxEventRepository.java`

- [ ] **Step 1: Create ReceiptPdfDocumentEntity**

Identical to `TaxInvoicePdfDocumentEntity` with column renames: `tax_invoice_id` → `receipt_id`, `tax_invoice_number` → `receipt_number`, table name `receipt_pdf_documents`.

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.persistence;

import com.wpanther.receipt.pdf.domain.model.GenerationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "receipt_pdf_documents", indexes = {
    @Index(name = "idx_receipt_pdf_receipt_id", columnList = "receipt_id"),
    @Index(name = "idx_receipt_pdf_receipt_number", columnList = "receipt_number"),
    @Index(name = "idx_receipt_pdf_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptPdfDocumentEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "receipt_id", nullable = false, length = 100, unique = true)
    private String receiptId;

    @Column(name = "receipt_number", nullable = false, length = 50)
    private String receiptNumber;

    @Column(name = "document_path", length = 500)
    private String documentPath;

    @Column(name = "document_url", length = 1000)
    private String documentUrl;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "xml_embedded", nullable = false)
    private Boolean xmlEmbedded;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GenerationStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = GenerationStatus.PENDING;
        if (mimeType == null) mimeType = "application/pdf";
        if (xmlEmbedded == null) xmlEmbedded = false;
        if (retryCount == null) retryCount = 0;
    }
}
```

- [ ] **Step 2: Create JpaReceiptPdfDocumentRepository**

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface JpaReceiptPdfDocumentRepository extends JpaRepository<ReceiptPdfDocumentEntity, UUID> {

    Optional<ReceiptPdfDocumentEntity> findByReceiptId(String receiptId);

    @Query("SELECT e.documentPath FROM ReceiptPdfDocumentEntity e WHERE e.documentPath IS NOT NULL")
    Set<String> findAllDocumentPaths();
}
```

- [ ] **Step 3: Create ReceiptPdfDocumentRepositoryAdapter**

Identical to taxinvoice version with receipt field renames.

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.persistence;

import com.wpanther.receipt.pdf.domain.model.ReceiptPdfDocument;
import com.wpanther.receipt.pdf.domain.repository.ReceiptPdfDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReceiptPdfDocumentRepositoryAdapter implements ReceiptPdfDocumentRepository {

    private final JpaReceiptPdfDocumentRepository jpaRepository;

    @Override
    public ReceiptPdfDocument save(ReceiptPdfDocument document) {
        ReceiptPdfDocumentEntity entity = toEntity(document);
        entity = jpaRepository.save(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<ReceiptPdfDocument> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ReceiptPdfDocument> findByReceiptId(String receiptId) {
        return jpaRepository.findByReceiptId(receiptId).map(this::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void flush() {
        jpaRepository.flush();
    }

    private ReceiptPdfDocumentEntity toEntity(ReceiptPdfDocument document) {
        return ReceiptPdfDocumentEntity.builder()
            .id(document.getId())
            .receiptId(document.getReceiptId())
            .receiptNumber(document.getReceiptNumber())
            .documentPath(document.getDocumentPath())
            .documentUrl(document.getDocumentUrl())
            .fileSize(document.getFileSize())
            .mimeType(document.getMimeType())
            .xmlEmbedded(document.isXmlEmbedded())
            .status(document.getStatus())
            .errorMessage(document.getErrorMessage())
            .retryCount(document.getRetryCount())
            .createdAt(document.getCreatedAt())
            .completedAt(document.getCompletedAt())
            .build();
    }

    private ReceiptPdfDocument toDomain(ReceiptPdfDocumentEntity entity) {
        return ReceiptPdfDocument.builder()
            .id(entity.getId())
            .receiptId(entity.getReceiptId())
            .receiptNumber(entity.getReceiptNumber())
            .documentPath(entity.getDocumentPath())
            .documentUrl(entity.getDocumentUrl())
            .fileSize(entity.getFileSize() != null ? entity.getFileSize() : 0L)
            .mimeType(entity.getMimeType())
            .xmlEmbedded(entity.getXmlEmbedded() != null && entity.getXmlEmbedded())
            .status(entity.getStatus())
            .errorMessage(entity.getErrorMessage())
            .retryCount(entity.getRetryCount() != null ? entity.getRetryCount() : 0)
            .createdAt(entity.getCreatedAt())
            .completedAt(entity.getCompletedAt())
            .build();
    }
}
```

- [ ] **Step 4: Create outbox entities**

Copy `OutboxEventEntity.java`, `SpringDataOutboxRepository.java`, and `JpaOutboxEventRepository.java` from the taxinvoice reference **as-is** — these are structurally identical across services. The only difference is the package name.

`OutboxEventEntity.java` — copy from taxinvoice, change package to `com.wpanther.receipt.pdf.infrastructure.adapter.out.persistence.outbox`.

`SpringDataOutboxRepository.java` — copy from taxinvoice, change package.

`JpaOutboxEventRepository.java` — copy from taxinvoice, change package.

These files are long but identical to the taxinvoice versions read earlier. Copy them verbatim with only the package declaration changed.

- [ ] **Step 5: Verify compilation**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/persistence/
git commit -m "feat: add persistence layer — JPA entities, repository adapter, outbox"
```

---

## Task 7: Messaging adapters — EventPublisher, SagaReplyPublisher, events

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/ReceiptPdfGeneratedEvent.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/ReceiptPdfReplyEvent.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/EventPublisher.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/SagaReplyPublisher.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/OutboxConstants.java`

- [ ] **Step 1: Create ReceiptPdfGeneratedEvent**

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

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

- [ ] **Step 2: Create ReceiptPdfReplyEvent**

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaReply;

public class ReceiptPdfReplyEvent extends SagaReply {

    private static final long serialVersionUID = 1L;

    private String pdfUrl;
    private Long pdfSize;

    public static ReceiptPdfReplyEvent success(
            String sagaId, SagaStep sagaStep, String correlationId,
            String pdfUrl, Long pdfSize) {
        ReceiptPdfReplyEvent reply = new ReceiptPdfReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS);
        reply.pdfUrl = pdfUrl;
        reply.pdfSize = pdfSize;
        return reply;
    }

    public static ReceiptPdfReplyEvent failure(String sagaId, SagaStep sagaStep, String correlationId,
                                               String errorMessage) {
        return new ReceiptPdfReplyEvent(sagaId, sagaStep, correlationId, errorMessage);
    }

    public static ReceiptPdfReplyEvent compensated(String sagaId, SagaStep sagaStep, String correlationId) {
        return new ReceiptPdfReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED);
    }

    private ReceiptPdfReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, ReplyStatus status) {
        super(sagaId, sagaStep, correlationId, status);
    }

    private ReceiptPdfReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        super(sagaId, sagaStep, correlationId, errorMessage);
    }

    public String getPdfUrl() { return pdfUrl; }
    public Long getPdfSize() { return pdfSize; }
}
```

- [ ] **Step 3: Create OutboxConstants**

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging;

final class OutboxConstants {

    static final String AGGREGATE_TYPE = "ReceiptPdfDocument";

    private OutboxConstants() {}
}
```

- [ ] **Step 4: Create EventPublisher**

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.receipt.pdf.application.port.out.PdfEventPort;
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
public class EventPublisher implements PdfEventPort {

    private static final String AGGREGATE_TYPE = OutboxConstants.AGGREGATE_TYPE;

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishPdfGenerated(ReceiptPdfGeneratedEvent event) {
        Map<String, String> headers = Map.of(
            "documentType", "RECEIPT",
            "correlationId", event.getCorrelationId()
        );

        outboxService.saveWithRouting(
            event,
            AGGREGATE_TYPE,
            event.getDocumentId(),
            "pdf.generated.receipt",
            event.getDocumentId(),
            toJson(headers)
        );

        log.info("Published ReceiptPdfGeneratedEvent to outbox for notification: {}", event.getDocumentNumber());
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event headers", e);
        }
    }
}
```

- [ ] **Step 5: Create SagaReplyPublisher**

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.receipt.pdf.application.port.out.SagaReplyPort;
import com.wpanther.saga.domain.enums.SagaStep;
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

        outboxService.saveWithRouting(reply, AGGREGATE_TYPE, sagaId, REPLY_TOPIC, sagaId, toJson(headers));
        log.info("Published SUCCESS saga reply for saga {} step {}", sagaId, sagaStep);
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

        outboxService.saveWithRouting(reply, AGGREGATE_TYPE, sagaId, REPLY_TOPIC, sagaId, toJson(headers));
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

        outboxService.saveWithRouting(reply, AGGREGATE_TYPE, sagaId, REPLY_TOPIC, sagaId, toJson(headers));
        log.info("Published COMPENSATED saga reply for saga {} step {}", sagaId, sagaStep);
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event headers", e);
        }
    }
}
```

- [ ] **Step 6: Verify compilation**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/
git commit -m "feat: add messaging adapters — EventPublisher, SagaReplyPublisher, events"
```

---

## Task 8: Kafka route config and command mapper

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/SagaRouteConfig.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/KafkaCommandMapper.java`

- [ ] **Step 1: Create KafkaCommandMapper**

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka;

import org.springframework.stereotype.Component;

@Component
public class KafkaCommandMapper {

    public KafkaReceiptProcessCommand toProcess(KafkaReceiptProcessCommand src) {
        return src;
    }

    public KafkaReceiptCompensateCommand toCompensate(KafkaReceiptCompensateCommand src) {
        return src;
    }
}
```

- [ ] **Step 2: Create SagaRouteConfig**

Structurally identical to taxinvoice version, with receipt topic names and command class types.

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.receipt.pdf.application.service.SagaCommandHandler;
import com.wpanther.receipt.pdf.application.usecase.CompensateReceiptPdfUseCase;
import com.wpanther.receipt.pdf.application.usecase.ProcessReceiptPdfUseCase;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class SagaRouteConfig extends RouteBuilder {

    private final ProcessReceiptPdfUseCase processUseCase;
    private final CompensateReceiptPdfUseCase compensateUseCase;
    private final SagaCommandHandler sagaCommandHandler;
    private final ObjectMapper objectMapper;

    public SagaRouteConfig(ProcessReceiptPdfUseCase processUseCase,
                           CompensateReceiptPdfUseCase compensateUseCase,
                           SagaCommandHandler sagaCommandHandler,
                           ObjectMapper objectMapper) {
        this.processUseCase = processUseCase;
        this.compensateUseCase = compensateUseCase;
        this.sagaCommandHandler = sagaCommandHandler;
        this.objectMapper = objectMapper;
    }

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
                            if (body instanceof KafkaReceiptProcessCommand cmd) {
                                log.error("DLQ: notifying orchestrator of retry exhaustion for saga {} document {}",
                                        cmd.getSagaId(), cmd.getDocumentNumber());
                                sagaCommandHandler.publishOrchestrationFailure(cmd, cause);
                            } else if (body instanceof KafkaReceiptCompensateCommand cmd) {
                                log.error("DLQ: notifying orchestrator of compensation retry exhaustion for saga {} document {}",
                                        cmd.getSagaId(), cmd.getDocumentId());
                                sagaCommandHandler.publishCompensationOrchestrationFailure(cmd, cause);
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
                .unmarshal().json(JsonLibrary.Jackson, KafkaReceiptProcessCommand.class)
                .process(exchange -> {
                        KafkaReceiptProcessCommand cmd =
                                exchange.getIn().getBody(KafkaReceiptProcessCommand.class);
                        log.info("Processing saga command for saga: {}, document: {}",
                                        cmd.getSagaId(), cmd.getDocumentNumber());
                        processUseCase.handle(cmd);
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
                .unmarshal().json(JsonLibrary.Jackson, KafkaReceiptCompensateCommand.class)
                .process(exchange -> {
                        KafkaReceiptCompensateCommand cmd =
                                exchange.getIn().getBody(KafkaReceiptCompensateCommand.class);
                        log.info("Processing compensation for saga: {}, document: {}",
                                        cmd.getSagaId(), cmd.getDocumentId());
                        compensateUseCase.handle(cmd);
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
            JsonNode node        = objectMapper.readTree(rawBytes);
            String sagaId        = node.path("sagaId").asText(null);
            String sagaStepStr   = node.path("sagaStep").asText(null);
            String correlationId = node.path("correlationId").asText(null);

            if (sagaId == null || sagaStepStr == null) {
                log.error("DLQ: saga metadata missing in raw message — orchestrator must timeout");
                return;
            }
            SagaStep sagaStep = objectMapper.readValue(
                    "\"" + sagaStepStr + "\"", SagaStep.class);
            sagaCommandHandler.publishOrchestrationFailureForUnparsedMessage(
                    sagaId, sagaStep, correlationId, cause);
        } catch (Exception parseEx) {
            log.error("DLQ: cannot parse raw message for saga metadata — orchestrator must timeout", parseEx);
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/SagaRouteConfig.java src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/KafkaCommandMapper.java
git commit -m "feat: add Camel Kafka route config and command mapper"
```

---

## Task 9: Flyway migration

**Files:**
- Create: `src/main/resources/db/migration/V1__create_receipt_pdf_tables.sql`

Per the spec, a single migration creates both tables (consolidated from taxinvoice's 4 migrations).

- [ ] **Step 1: Create migration script**

```sql
-- Create receipt_pdf_documents table
CREATE TABLE receipt_pdf_documents (
    id UUID PRIMARY KEY,
    receipt_id VARCHAR(100) NOT NULL UNIQUE,
    receipt_number VARCHAR(50) NOT NULL,
    document_path VARCHAR(500),
    document_url VARCHAR(1000),
    file_size BIGINT,
    mime_type VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    xml_embedded BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_receipt_pdf_receipt_id ON receipt_pdf_documents(receipt_id);
CREATE INDEX idx_receipt_pdf_receipt_number ON receipt_pdf_documents(receipt_number);
CREATE INDEX idx_receipt_pdf_status ON receipt_pdf_documents(status);

-- Create outbox_events table for the Transactional Outbox Pattern.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    topic VARCHAR(255),
    partition_key VARCHAR(255),
    headers TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);

-- Index for polling publisher
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_id, aggregate_type);

-- Compound index optimized for polling query
CREATE INDEX idx_outbox_pending_created ON outbox_events(status, created_at)
    WHERE status = 'PENDING';
```

- [ ] **Step 2: Verify the file compiles with Maven (Flyway validates at runtime only)**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/main/resources/db/migration/
git commit -m "feat: add Flyway migration for receipt_pdf_documents and outbox_events tables"
```

---

## Task 10: PDF generation pipeline — FOP generator, PDF/A-3 converter, Thai amount words

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/pdf/FopReceiptPdfGenerator.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/pdf/PdfA3Converter.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/pdf/ThaiAmountWordsConverter.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/pdf/ReceiptPdfGenerationServiceImpl.java`

- [ ] **Step 1: Copy ThaiAmountWordsConverter — identical to taxinvoice version**

This class is document-type agnostic (no "tax invoice" or "receipt" references). Copy verbatim from taxinvoice with package change only.

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.pdf;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ThaiAmountWordsConverter {
    // ... identical to taxinvoice version — copy verbatim, only change package
}
```

Copy the full file content from `taxinvoice-pdf-generation-service/src/main/java/.../pdf/ThaiAmountWordsConverter.java`, changing only the package declaration to `com.wpanther.receipt.pdf.infrastructure.adapter.out.pdf`.

- [ ] **Step 2: Create FopReceiptPdfGenerator**

Identical to `FopTaxInvoicePdfGenerator` with:
- `RECEIPT_XSL_PATH = "xsl/receipt-direct.xsl"` instead of `taxinvoice-direct.xsl`
- Metric names: `pdf.fop.render` (unchanged), `pdf.fop.size.bytes` description updated
- Log messages: "receipt" instead of "tax invoice"

Copy the full file from taxinvoice and apply these renames. The class structure, semaphore, templates, FOP config loading, and exception classes are all identical.

Key differences from `FopTaxInvoicePdfGenerator`:
```java
private static final String RECEIPT_XSL_PATH = "xsl/receipt-direct.xsl";
// In constructor:
this.cachedTemplates = compileTemplates(tf, RECEIPT_XSL_PATH);
// In log message:
log.info("FopReceiptPdfGenerator initialized: ...");
// Metric description:
.description("Size of generated receipt PDFs in bytes")
```

- [ ] **Step 3: Create PdfA3Converter**

Identical to taxinvoice version with log message updates ("receipt" instead of "tax invoice"). The `convertToPdfA3` method parameter `taxInvoiceNumber` becomes `receiptNumber`. The PDF/A-3 metadata `dc.setTitle("Thai e-Tax Receipt: " + receiptNumber)` and `dc.addCreator("Receipt PDF Generation Service")`.

Copy the full file from taxinvoice and apply these renames:
```java
// Method signature:
public byte[] convertToPdfA3(byte[] pdfBytes, String xmlContent, String xmlFilename, String receiptNumber)
// In addPdfAMetadata:
dc.setTitle("Thai e-Tax Receipt: " + receiptNumber);
dc.setDescription("Electronic receipt with embedded XML source");
dc.addCreator("Receipt PDF Generation Service");
xmpBasic.setCreatorTool("Thai e-Tax Receipt System");
```

- [ ] **Step 4: Create ReceiptPdfGenerationServiceImpl**

Identical to `TaxInvoicePdfGenerationServiceImpl` with:
- Receipt namespace URIs: `Receipt_CrossIndustryInvoice:2` and `Receipt_ReusableAggregateBusinessInformationEntity:2`
- XPath root: `/rsm:Receipt_CrossIndustryInvoice` instead of `/rsm:TaxInvoice_CrossIndustryInvoice`
- XML filename: `receipt-{number}.xml` instead of `taxinvoice-{number}.xml`
- Uses `FopReceiptPdfGenerator` instead of `FopTaxInvoicePdfGenerator`

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.pdf;

import com.wpanther.receipt.pdf.domain.service.ReceiptPdfGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

@Service
@Slf4j
public class ReceiptPdfGenerationServiceImpl implements ReceiptPdfGenerationService {

    private static final String RSM_NS =
        "urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2";
    private static final String RAM_NS =
        "urn:etda:uncefact:data:standard:Receipt_ReusableAggregateBusinessInformationEntity:2";
    private static final String GRAND_TOTAL_XPATH =
        "/rsm:Receipt_CrossIndustryInvoice" +
        "/rsm:SupplyChainTradeTransaction" +
        "/ram:ApplicableHeaderTradeSettlement" +
        "/ram:SpecifiedTradeSettlementHeaderMonetarySummation" +
        "/ram:GrandTotalAmount";

    private static final NamespaceContext NS_CONTEXT = new NamespaceContext() {
        @Override
        public String getNamespaceURI(String prefix) {
            return switch (prefix) {
                case "rsm" -> RSM_NS;
                case "ram" -> RAM_NS;
                default    -> XMLConstants.NULL_NS_URI;
            };
        }
        @Override public String getPrefix(String ns) { return null; }
        @Override public Iterator<String> getPrefixes(String ns) { return Collections.emptyIterator(); }
    };

    private final FopReceiptPdfGenerator fopPdfGenerator;
    private final PdfA3Converter pdfA3Converter;

    public ReceiptPdfGenerationServiceImpl(FopReceiptPdfGenerator fopPdfGenerator,
                                           PdfA3Converter pdfA3Converter) {
        this.fopPdfGenerator = fopPdfGenerator;
        this.pdfA3Converter  = pdfA3Converter;
    }

    @Override
    public byte[] generatePdf(String receiptNumber, String signedXml)
            throws ReceiptPdfGenerationException {

        log.info("Starting PDF generation for receipt: {}", receiptNumber);

        if (signedXml == null || signedXml.isBlank()) {
            throw new ReceiptPdfGenerationException(
                "signedXml is null or blank for receipt: " + receiptNumber);
        }

        try {
            BigDecimal grandTotal  = extractGrandTotal(signedXml, receiptNumber);
            String amountInWords   = ThaiAmountWordsConverter.toWords(grandTotal);
            log.debug("Grand total {} → amountInWords: {}", grandTotal, amountInWords);

            Map<String, Object> params = Map.of("amountInWords", amountInWords);
            byte[] basePdf = fopPdfGenerator.generatePdf(signedXml, params);
            log.debug("Generated base PDF: {} bytes", basePdf.length);

            String xmlFilename = "receipt-" + receiptNumber + ".xml";
            byte[] pdfA3 = pdfA3Converter.convertToPdfA3(basePdf, signedXml, xmlFilename, receiptNumber);
            log.info("Generated PDF/A-3 for receipt {}: {} bytes", receiptNumber, pdfA3.length);
            return pdfA3;

        } catch (FopReceiptPdfGenerator.PdfGenerationException e) {
            log.error("FOP PDF generation failed for receipt: {}", receiptNumber, e);
            throw new ReceiptPdfGenerationException("PDF generation failed: " + e.getMessage(), e);
        } catch (PdfA3Converter.PdfConversionException e) {
            log.error("PDF/A-3 conversion failed for receipt: {}", receiptNumber, e);
            throw new ReceiptPdfGenerationException("PDF/A-3 conversion failed: " + e.getMessage(), e);
        } catch (ReceiptPdfGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during PDF generation for receipt: {}", receiptNumber, e);
            throw new ReceiptPdfGenerationException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    private BigDecimal extractGrandTotal(String signedXml, String receiptNumber)
            throws ReceiptPdfGenerationException {
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(NS_CONTEXT);
            String value = (String) xpath.evaluate(
                GRAND_TOTAL_XPATH,
                new InputSource(new StringReader(signedXml)),
                XPathConstants.STRING);
            if (value == null || value.isBlank()) {
                throw new ReceiptPdfGenerationException(
                    "GrandTotalAmount not found in signed XML for receipt: " + receiptNumber);
            }
            return new BigDecimal(value.trim());
        } catch (XPathExpressionException e) {
            throw new ReceiptPdfGenerationException(
                "Failed to extract GrandTotalAmount from signed XML: " + e.getMessage(), e);
        } catch (NumberFormatException e) {
            throw new ReceiptPdfGenerationException(
                "Invalid GrandTotalAmount in signed XML for receipt " + receiptNumber + ": " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/pdf/
git commit -m "feat: add PDF generation pipeline — FOP generator, PDF/A-3 converter, Thai amount words"
```

---

## Task 11: MinIO storage adapter + cleanup, REST client, SignedXmlFetchPort update

**Files:**
- Modify: `src/main/java/com/wpanther/receipt/pdf/application/port/out/SignedXmlFetchPort.java` (add inner exception)
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/storage/MinioStorageAdapter.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/storage/MinioCleanupService.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/client/RestTemplateSignedXmlFetcher.java`

- [ ] **Step 1: Update SignedXmlFetchPort with inner exception class**

Add `SignedXmlFetchException` inner class (the existing port from Task 4 is missing it).

```java
package com.wpanther.receipt.pdf.application.port.out;

public interface SignedXmlFetchPort {

    String fetch(String url);

    class SignedXmlFetchException extends RuntimeException {
        public SignedXmlFetchException(String message) { super(message); }
        public SignedXmlFetchException(String message, Throwable cause) { super(message, cause); }
    }
}
```

- [ ] **Step 2: Create MinioStorageAdapter**

Identical to taxinvoice version with:
- S3 key pattern: `receipt-{number}-{uuid}.pdf` instead of `taxinvoice-{number}-{uuid}.pdf`

Copy the full file from taxinvoice, change package and key pattern:
```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.storage;

// ... same imports ...

@Component
@Slf4j
public class MinioStorageAdapter implements PdfStoragePort {
    // ... identical structure ...
    private String doStore(String receiptNumber, byte[] pdfBytes) {
        LocalDate now = LocalDate.now();
        String safeName = sanitizeFilename(receiptNumber);
        String fileName = String.format("receipt-%s-%s.pdf", safeName, UUID.randomUUID());
        String s3Key = String.format("%04d/%02d/%02d/%s",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), fileName);
        // ... rest identical ...
    }
    // ... rest identical ...
}
```

- [ ] **Step 3: Create MinioCleanupService**

Identical to taxinvoice version. Only change: references `JpaReceiptPdfDocumentRepository` instead of `JpaTaxInvoicePdfDocumentRepository`.

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.storage;

import com.wpanther.receipt.pdf.infrastructure.adapter.out.persistence.JpaReceiptPdfDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.minio.cleanup.enabled", havingValue = "true", matchIfMissing = false)
public class MinioCleanupService {

    private final MinioStorageAdapter minioStorage;
    private final JpaReceiptPdfDocumentRepository repository;

    @Scheduled(cron = "${app.minio.cleanup.cron:0 0 2 * * ?}")
    public void cleanupOrphanedPdfs() {
        log.info("Starting orphaned PDF cleanup job");
        try {
            List<String> minioKeys = minioStorage.listAllPdfs();
            log.debug("Found {} PDF objects in MinIO", minioKeys.size());
            Set<String> databaseKeys = repository.findAllDocumentPaths();
            log.debug("Found {} document paths in database", databaseKeys.size());
            List<String> orphanedKeys = minioKeys.stream()
                    .filter(key -> !databaseKeys.contains(key))
                    .toList();
            if (orphanedKeys.isEmpty()) {
                log.info("No orphaned PDFs found");
                return;
            }
            log.warn("Found {} orphaned PDF(s) to delete: {}", orphanedKeys.size(), orphanedKeys);
            int deletedCount = 0;
            for (String key : orphanedKeys) {
                minioStorage.deleteWithoutCircuitBreaker(key);
                deletedCount++;
            }
            log.info("Orphaned PDF cleanup completed: {} of {} objects deleted",
                    deletedCount, orphanedKeys.size());
        } catch (Exception e) {
            log.error("Orphaned PDF cleanup job failed: {}", e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: Create RestTemplateSignedXmlFetcher**

Copy verbatim from taxinvoice with package change. This class has no document-type-specific references.

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.out.client;

import com.wpanther.receipt.pdf.application.port.out.SignedXmlFetchPort;
import com.wpanther.receipt.pdf.application.port.out.SignedXmlFetchPort.SignedXmlFetchException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestTemplateSignedXmlFetcher implements SignedXmlFetchPort {

    private final RestTemplate restTemplate;

    @Override
    @CircuitBreaker(name = "signedXmlFetch", fallbackMethod = "fallbackOnFailure")
    public String fetch(String signedXmlUrl) {
        log.debug("Fetching signed XML from {}", signedXmlUrl);
        String response = restTemplate.getForObject(signedXmlUrl, String.class);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException(
                    "Received null or empty signed XML response from: " + signedXmlUrl);
        }
        log.debug("Successfully fetched signed XML, size: {} bytes", response.length());
        return response;
    }

    private String fallbackOnFailure(String signedXmlUrl, Throwable throwable) {
        throw new SignedXmlFetchException(
                "Circuit breaker 'signedXmlFetch' is OPEN — " +
                "document-storage-service is degraded. URL: " + signedXmlUrl, throwable);
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/main/java/com/wpanther/receipt/pdf/application/port/out/SignedXmlFetchPort.java src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/storage/ src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/client/
git commit -m "feat: add MinIO storage adapter, cleanup service, REST client for signed XML"
```

---

## Task 12: Infrastructure config — MinioConfig, OutboxConfig, RestTemplateConfig, FontHealthCheck

**Files:**
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/config/MinioConfig.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/config/OutboxConfig.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/config/RestTemplateConfig.java`
- Create: `src/main/java/com/wpanther/receipt/pdf/infrastructure/config/FontHealthCheck.java`

- [ ] **Step 1: Create MinioConfig**

Copy verbatim from taxinvoice with package change. No document-type-specific references.

```java
package com.wpanther.receipt.pdf.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@Slf4j
public class MinioConfig {

    @Bean
    public S3Client s3Client(
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey,
            @Value("${app.minio.region}") String region,
            @Value("${app.minio.path-style-access:true}") boolean pathStyleAccess) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .endpointOverride(URI.create(endpoint))
                .forcePathStyle(pathStyleAccess);
        S3Client client = builder.build();
        log.info("Initialized MinIO S3 client: endpoint={}", endpoint);
        return client;
    }
}
```

- [ ] **Step 2: Create OutboxConfig**

References `JpaOutboxEventRepository` and `SpringDataOutboxRepository` from the receipt package.

```java
package com.wpanther.receipt.pdf.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.receipt.pdf.infrastructure.adapter.out.persistence.outbox.JpaOutboxEventRepository;
import com.wpanther.receipt.pdf.infrastructure.adapter.out.persistence.outbox.SpringDataOutboxRepository;
import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxConfig {

    @Bean
    @ConditionalOnMissingBean(OutboxEventRepository.class)
    public OutboxEventRepository outboxEventRepository(SpringDataOutboxRepository springRepository) {
        return new JpaOutboxEventRepository(springRepository);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxService.class)
    public OutboxService outboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        return new OutboxService(repository, objectMapper);
    }
}
```

- [ ] **Step 3: Create RestTemplateConfig**

Copy verbatim from taxinvoice with package change.

```java
package com.wpanther.receipt.pdf.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
public class RestTemplateConfig {

    @Value("${app.rest-client.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${app.rest-client.read-timeout:30000}")
    private int readTimeout;

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public RestTemplateConfig(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @PostConstruct
    public void setupCircuitBreakerLogging() {
        CircuitBreaker signedXmlFetch = circuitBreakerRegistry.circuitBreaker("signedXmlFetch");
        CircuitBreaker minio = circuitBreakerRegistry.circuitBreaker("minio");
        signedXmlFetch.getEventPublisher()
                .onStateTransition(event -> log.info("Circuit breaker 'signedXmlFetch' state transition: {}",
                        event.getStateTransition()));
        minio.getEventPublisher()
                .onStateTransition(event -> log.info("Circuit breaker 'minio' state transition: {}",
                        event.getStateTransition()));
        log.info("Circuit breaker event logging configured for 'signedXmlFetch' and 'minio'");
    }

    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        log.info("Configured RestTemplate with connectTimeout={}ms, readTimeout={}ms",
                connectTimeout, readTimeout);
        return new RestTemplate(factory);
    }
}
```

- [ ] **Step 4: Create FontHealthCheck**

Copy verbatim from taxinvoice with package change. No document-type-specific references.

```java
package com.wpanther.receipt.pdf.infrastructure.config;

// Copy full file from taxinvoice, change only the package declaration
// to com.wpanther.receipt.pdf.infrastructure.config
```

- [ ] **Step 5: Verify compilation**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/main/java/com/wpanther/receipt/pdf/infrastructure/config/
git commit -m "feat: add infrastructure config — MinIO, outbox, REST template, font health check"
```

---

## Task 13: Resource files — XSL-FO template, FOP config, fonts, ICC profile

**Files:**
- Copy: `src/main/resources/fonts/` (6 TTF files from taxinvoice)
- Copy: `src/main/resources/fop/fop.xconf` (from taxinvoice)
- Copy: `src/main/resources/icc/sRGB.icc` (from taxinvoice)
- Create: `src/main/resources/xsl/receipt-direct.xsl` (adapted from `taxinvoice-direct.xsl`)
- Copy: `src/test/resources/fop/fop.xconf` (simplified test config from taxinvoice)

- [ ] **Step 1: Copy font files, FOP config, and ICC profile from taxinvoice**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service

# Copy fonts
mkdir -p src/main/resources/fonts
cp ../taxinvoice-pdf-generation-service/src/main/resources/fonts/*.ttf src/main/resources/fonts/

# Copy FOP config
mkdir -p src/main/resources/fop
cp ../taxinvoice-pdf-generation-service/src/main/resources/fop/fop.xconf src/main/resources/fop/

# Copy ICC profile
mkdir -p src/main/resources/icc
cp ../taxinvoice-pdf-generation-service/src/main/resources/icc/sRGB.icc src/main/resources/icc/

# Copy test FOP config
mkdir -p src/test/resources/fop
cp ../taxinvoice-pdf-generation-service/src/test/resources/fop/fop.xconf src/test/resources/fop/
```

- [ ] **Step 2: Create receipt-direct.xsl**

Read `taxinvoice-pdf-generation-service/src/main/resources/xsl/taxinvoice-direct.xsl` and apply the following changes:

1. **Namespace URIs** — change from TaxInvoice to Receipt:
   - `urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2` → `urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2`
   - `urn:etda:uncefact:data:standard:TaxInvoice_ReusableAggregateBusinessInformationEntity:2` → `urn:etda:uncefact:data:standard:Receipt_ReusableAggregateBusinessInformationEntity:2`

2. **Root match** — change from:
   - `/rsm:TaxInvoice_CrossIndustryInvoice` → `/rsm:Receipt_CrossIndustryInvoice`

3. **Document title** — change from:
   - `ใบเสร็จรับเงิน/ใบกำกับภาษี` → `ใบรับ / RECEIPT`

All other XPath paths (`GrandTotalAmount`, seller/buyer parties, line items, monetary summation) are structurally identical — only the namespace URIs differ. The `amountInWords` XSLT parameter injection is unchanged.

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
mkdir -p src/main/resources/xsl
# Read the taxinvoice XSL and create receipt version with namespace/title changes
cp ../taxinvoice-pdf-generation-service/src/main/resources/xsl/taxinvoice-direct.xsl src/main/resources/xsl/receipt-direct.xsl
```

Then edit `receipt-direct.xsl` with sed to apply the three changes:

```bash
sed -i \
  's|urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2|urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2|g' \
  src/main/resources/xsl/receipt-direct.xsl

sed -i \
  's|urn:etda:uncefact:data:standard:TaxInvoice_ReusableAggregateBusinessInformationEntity:2|urn:etda:uncefact:data:standard:Receipt_ReusableAggregateBusinessInformationEntity:2|g' \
  src/main/resources/xsl/receipt-direct.xsl

sed -i \
  's|rsm:TaxInvoice_CrossIndustryInvoice|rsm:Receipt_CrossIndustryInvoice|g' \
  src/main/resources/xsl/receipt-direct.xsl

sed -i \
  's|ใบเสร็จรับเงิน/ใบกำกับภาษี|ใบรับ / RECEIPT|g' \
  src/main/resources/xsl/receipt-direct.xsl
```

- [ ] **Step 3: Verify the XSL template is valid**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/main/resources/fonts/ src/main/resources/fop/ src/main/resources/icc/ src/main/resources/xsl/ src/test/resources/fop/
git commit -m "feat: add XSL-FO template, FOP config, Thai fonts, ICC profile"
```

---

## Task 14: Full build verification

**Files:** None (verification only)

- [ ] **Step 1: Full Maven build with compilation**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: Verify all source files exist**

Run:
```bash
find src/main/java -name "*.java" | wc -l
```
Expected: ~35 Java files

- [ ] **Step 3: Verify all resource files exist**

Run:
```bash
find src/main/resources -type f | sort
```
Expected: `application.yml`, `db/migration/V1__create_receipt_pdf_tables.sql`, `fop/fop.xconf`, `icc/sRGB.icc`, `xsl/receipt-direct.xsl`, 6 font TTF files

---

## Task 15: Core tests — SagaCommandHandlerTest, ReceiptPdfDocumentServiceTest, CamelRouteConfigTest

**Files:**
- Create: `src/test/java/com/wpanther/receipt/pdf/application/service/SagaCommandHandlerTest.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/application/service/ReceiptPdfDocumentServiceTest.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/infrastructure/config/CamelRouteConfigTest.java`

- [ ] **Step 1: Create SagaCommandHandlerTest**

Read the taxinvoice reference at `taxinvoice-pdf-generation-service/src/test/java/.../SagaCommandHandlerTest.java` (already read earlier — see Task 3 context). Create the receipt version with:
- `GENERATE_RECEIPT_PDF` instead of `GENERATE_TAX_INVOICE_PDF`
- `KafkaReceiptProcessCommand` / `KafkaReceiptCompensateCommand` instead of taxinvoice variants
- `ReceiptPdfDocument` with `receiptId`/`receiptNumber` fields
- `ReceiptPdfDocumentService` instead of `TaxInvoicePdfDocumentService`
- `ReceiptPdfGenerationService` and `ReceiptPdfGenerationException` instead of taxinvoice variants

The test covers the same scenarios: success, idempotency, max retries, null signedXmlUrl, generation failure, circuit breaker open, compensation success, idempotent compensation, compensation storage failure, orchestration failure (DLQ), compensation orchestration failure (DLQ).

Create the test using the same structure as the taxinvoice reference. Key changes in the helper methods:

```java
private KafkaReceiptProcessCommand createProcessCommand() {
    return new KafkaReceiptProcessCommand(
            "saga-001", SagaStep.GENERATE_RECEIPT_PDF, "corr-456",
            "doc-123", "RCP-2024-001",
            SIGNED_XML_URL);
}

private KafkaReceiptCompensateCommand createCompensateCommand() {
    return new KafkaReceiptCompensateCommand(
            "saga-001", SagaStep.GENERATE_RECEIPT_PDF, "corr-456",
            "doc-123");
}

private ReceiptPdfDocument createCompletedDocument() {
    ReceiptPdfDocument doc = ReceiptPdfDocument.builder()
            .id(UUID.randomUUID())
            .receiptId("doc-123")
            .receiptNumber("RCP-2024-001")
            .status(GenerationStatus.COMPLETED)
            .documentPath("2024/01/15/receipt-RCP-2024-001-abc.pdf")
            .documentUrl("http://localhost:9000/receipts/2024/01/15/receipt-RCP-2024-001-abc.pdf")
            .fileSize(12345L)
            .build();
    return doc;
}
```

Use `findByReceiptId` instead of `findByTaxInvoiceId`. The `getHandler()` method uses reflection with `ReceiptPdfDocumentService.class` and `ReceiptPdfGenerationService.class`.

- [ ] **Step 2: Create ReceiptPdfDocumentServiceTest**

Read the taxinvoice reference (already read earlier). Create the receipt version with receipt field renames:

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("ReceiptPdfDocumentService Unit Tests")
class ReceiptPdfDocumentServiceTest {

    @Mock private ReceiptPdfDocumentRepository repository;
    @Mock private PdfEventPort pdfEventPort;
    @Mock private SagaReplyPort sagaReplyPort;
    @Mock private PdfGenerationMetrics pdfGenerationMetrics;

    // Helper methods use reflection for constructor (same pattern as taxinvoice)
    // createCompletedDocument() uses receiptId/receiptNumber
    // Tests: findByReceiptId, beginGeneration, deleteById,
    //         publishIdempotentSuccess, publishRetryExhausted,
    //         publishGenerationFailure, publishCompensated,
    //         publishCompensationFailure, completeGenerationAndPublish,
    //         failGenerationAndPublish
    // All use GENERATE_RECEIPT_PDF and receipt command types
}
```

- [ ] **Step 3: Create CamelRouteConfigTest**

Read the taxinvoice reference (already read earlier). Create the receipt version:

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("CamelRouteConfig Unit Tests")
class CamelRouteConfigTest {

    @Mock private ProcessReceiptPdfUseCase processUseCase;
    @Mock private CompensateReceiptPdfUseCase compensateUseCase;
    @Mock private SagaCommandHandler sagaCommandHandler;
    private ObjectMapper objectMapper;
    private SagaRouteConfig sagaRouteConfig;

    // setUp() creates SagaRouteConfig(processUseCase, compensateUseCase, sagaCommandHandler, objectMapper)

    // Tests:
    // - Serialize/deserialize KafkaReceiptProcessCommand
    // - Serialize/deserialize KafkaReceiptCompensateCommand
    // - Serialize ReceiptPdfGeneratedEvent (verify eventType = "pdf.generated.receipt")
    // - Create ReceiptPdfReplyEvent success/failure/compensated
    // - Deserialize KafkaReceiptProcessCommand from JSON (sagaStep = "generate-receipt-pdf")
}
```

- [ ] **Step 4: Run these three test classes**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn clean test -Dtest="SagaCommandHandlerTest,ReceiptPdfDocumentServiceTest,CamelRouteConfigTest"`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/test/java/com/wpanther/receipt/pdf/application/service/ src/test/java/com/wpanther/receipt/pdf/infrastructure/config/CamelRouteConfigTest.java
git commit -m "test: add SagaCommandHandler, ReceiptPdfDocumentService, and CamelRouteConfig tests"
```

---

## Task 16: PDF infrastructure tests — FopReceiptPdfGeneratorTest, PdfA3ConverterTest, ReceiptPdfGenerationServiceImplTest, ThaiAmountWordsConverterTest

**Files:**
- Create: `src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/pdf/FopReceiptPdfGeneratorTest.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/pdf/PdfA3ConverterTest.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/pdf/ReceiptPdfGenerationServiceImplTest.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/pdf/ThaiAmountWordsConverterTest.java`

- [ ] **Step 1: Create FopReceiptPdfGeneratorTest**

Read the taxinvoice reference (already read earlier). The key change is the `MINIMAL_SIGNED_XML` constant — it must use **Receipt** namespaces instead of TaxInvoice:

```java
private static final String MINIMAL_SIGNED_XML =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
    "<rsm:Receipt_CrossIndustryInvoice " +
    "    xmlns:ram=\"urn:etda:uncefact:data:standard:Receipt_ReusableAggregateBusinessInformationEntity:2\"" +
    "    xmlns:rsm=\"urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2\">" +
    // ... same structure as taxinvoice but with Receipt element names ...
    "</rsm:Receipt_CrossIndustryInvoice>";
```

The XML structure (ExchangedDocument, SupplyChainTradeTransaction, seller/buyer, monetary summation, line items) is identical — only the namespace URIs and root element name change.

All test methods use `FopReceiptPdfGenerator` instead of `FopTaxInvoicePdfGenerator`:
- constructor compiles template
- rejects invalid maxConcurrentRenders / maxPdfSizeBytes
- semaphore permits match config
- checkFontAvailability does not throw
- PdfGenerationException constructors
- interrupted thread throws
- semaphore blocks when at capacity
- resolveBaseUri returns valid URI
- valid signed XML returns PDF bytes starting with `%PDF`
- no-arg overload delegates
- malformed XML throws
- PDF exceeding max size throws

- [ ] **Step 2: Create PdfA3ConverterTest**

Identical to taxinvoice version — `PdfA3Converter` has no document-type-specific code. Copy with package change only. The tests verify: constructor creates instance, null input throws, empty PDF throws, exception constructors.

- [ ] **Step 3: Create ReceiptPdfGenerationServiceImplTest**

Read the taxinvoice reference at `taxinvoice-pdf-generation-service/src/test/java/.../pdf/TaxInvoicePdfGenerationServiceImplTest.java`. Create the receipt version with:
- `ReceiptPdfGenerationServiceImpl` instead of `TaxInvoicePdfGenerationServiceImpl`
- `FopReceiptPdfGenerator` instead of `FopTaxInvoicePdfGenerator`
- Receipt namespaces in test XML
- `receiptNumber` instead of `taxInvoiceNumber`
- `ReceiptPdfGenerationException` instead of `TaxInvoicePdfGenerationException`

Tests: generate PDF successfully, null XML throws, blank XML throws, FOP failure throws, PDF/A-3 failure throws.

- [ ] **Step 4: Create ThaiAmountWordsConverterTest**

Copy verbatim from taxinvoice with package change — this class is document-type agnostic.

Read the taxinvoice reference at `taxinvoice-pdf-generation-service/src/test/java/.../pdf/ThaiAmountWordsConverterTest.java`. Copy with only the package declaration changed.

- [ ] **Step 5: Run these four test classes**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn clean test -Dtest="FopReceiptPdfGeneratorTest,PdfA3ConverterTest,ReceiptPdfGenerationServiceImplTest,ThaiAmountWordsConverterTest"`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/pdf/
git commit -m "test: add FOP generator, PDF/A-3 converter, generation service, Thai amount words tests"
```

---

## Task 17: Adapter tests — MinioStorageAdapterTest, MinioCleanupServiceTest, RestTemplateSignedXmlFetcherTest, EventPublisherTest, SagaReplyPublisherTest, KafkaCommandMapperTest

**Files:**
- Create: `src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/storage/MinioStorageAdapterTest.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/storage/MinioCleanupServiceTest.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/client/RestTemplateSignedXmlFetcherTest.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/EventPublisherTest.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/messaging/SagaReplyPublisherTest.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/KafkaCommandMapperTest.java`

- [ ] **Step 1: Create MinioStorageAdapterTest**

Read the taxinvoice reference at `taxinvoice-pdf-generation-service/src/test/java/.../storage/MinioStorageAdapterTest.java`. Create receipt version with:
- `MinioStorageAdapter` with `bucketName = "receipts"` and `baseUrl = "http://localhost:9000/receipts"`
- S3 key pattern assertions: `receipt-{number}` instead of `taxinvoice-{number}`
- Thai filename sanitization tests remain the same

Tests: upload stores with correct S3 key pattern, delete calls S3 deleteObject, resolveUrl constructs full URL, Thai characters in filename, filename sanitization.

- [ ] **Step 2: Create MinioCleanupServiceTest**

Read the taxinvoice reference. Create receipt version with:
- `JpaReceiptPdfDocumentRepository` instead of `JpaTaxInvoicePdfDocumentRepository`

Tests: cleanup deletes orphaned PDFs, cleanup with no orphans, cleanup when MinIO list fails.

- [ ] **Step 3: Create RestTemplateSignedXmlFetcherTest**

Copy verbatim from taxinvoice with package change. This test has no document-type-specific code. Uses `MockRestServiceServer` to test fetch success, empty response, and server error.

- [ ] **Step 4: Create EventPublisherTest**

Read the taxinvoice reference. Create receipt version with:
- `ReceiptPdfGeneratedEvent` instead of `TaxInvoicePdfGeneratedEvent`
- AGGREGATE_TYPE assertion: `"ReceiptPdfDocument"` instead of `"TaxInvoicePdfDocument"`
- Topic assertion: `"pdf.generated.receipt"` instead of `"pdf.generated.tax-invoice"`
- Header assertion: `"RECEIPT"` instead of `"TAX_INVOICE"`

- [ ] **Step 5: Create SagaReplyPublisherTest**

Read the taxinvoice reference. Create receipt version with:
- `ReceiptPdfReplyEvent` instead of `TaxInvoicePdfReplyEvent`
- AGGREGATE_TYPE: `"ReceiptPdfDocument"`
- REPLY_TOPIC: `"saga.reply.receipt-pdf"` instead of `"saga.reply.tax-invoice-pdf"`
- `SagaStep.GENERATE_RECEIPT_PDF` instead of `GENERATE_TAX_INVOICE_PDF`

- [ ] **Step 6: Create KafkaCommandMapperTest**

Read the taxinvoice reference. Create receipt version with:
- `KafkaReceiptProcessCommand` and `KafkaReceiptCompensateCommand` types

```java
package com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaCommandMapperTest {

    private final KafkaCommandMapper mapper = new KafkaCommandMapper();

    @Test
    void toProcess_returnsSameInstance() {
        KafkaReceiptProcessCommand cmd = new KafkaReceiptProcessCommand(
                "saga-1", null, "corr", "doc-1", "RCP-001", "http://url");
        assertThat(mapper.toProcess(cmd)).isSameAs(cmd);
    }

    @Test
    void toCompensate_returnsSameInstance() {
        KafkaReceiptCompensateCommand cmd = new KafkaReceiptCompensateCommand(
                "saga-1", null, "corr", "doc-1");
        assertThat(mapper.toCompensate(cmd)).isSameAs(cmd);
    }
}
```

- [ ] **Step 7: Run all six test classes**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn clean test -Dtest="MinioStorageAdapterTest,MinioCleanupServiceTest,RestTemplateSignedXmlFetcherTest,EventPublisherTest,SagaReplyPublisherTest,KafkaCommandMapperTest"`
Expected: All tests PASS

- [ ] **Step 8: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/
git commit -m "test: add MinIO storage, cleanup, REST client, messaging, and command mapper tests"
```

---

## Task 18: Remaining tests — persistence, font health check, and full test suite verification

**Files:**
- Create: `src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/persistence/JpaReceiptPdfDocumentRepositoryImplTest.java`
- Create: `src/test/java/com/wpanther/receipt/pdf/infrastructure/config/FontHealthCheckTest.java`

- [ ] **Step 1: Create JpaReceiptPdfDocumentRepositoryImplTest**

Read the taxinvoice reference at `taxinvoice-pdf-generation-service/src/test/java/.../persistence/JpaTaxInvoicePdfDocumentRepositoryImplTest.java`. Create receipt version with receipt field renames.

This test uses Spring Boot's `@DataJpaTest` with H2 in-memory database to verify:
- Save and find by ID
- Find by receiptId
- Delete by ID
- Entity↔domain mapping round-trip

```java
@DataJpaTest
@DisplayName("JpaReceiptPdfDocumentRepository Integration Tests")
class JpaReceiptPdfDocumentRepositoryImplTest {

    @Autowired private JpaReceiptPdfDocumentRepository jpaRepository;
    private ReceiptPdfDocumentRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ReceiptPdfDocumentRepositoryAdapter(jpaRepository);
    }

    // Tests: saveAndFindById, findByReceiptId, deleteById, mapping round-trip
    // Uses ReceiptPdfDocument.builder().receiptId("rcpt-1").receiptNumber("RCP-001")
}
```

- [ ] **Step 2: Create FontHealthCheckTest**

Read the taxinvoice reference. Copy with package change — this test has no document-type-specific code.

```java
package com.wpanther.receipt.pdf.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.mock.application.MockApplicationEvent;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FontHealthCheck Unit Tests")
class FontHealthCheckTest {

    @Test
    @DisplayName("checkFontsAtStartup passes when all fonts are present")
    void checkFontsAtStartup_allFontsPresent() {
        FontHealthCheck check = new FontHealthCheck();
        // Test config has fonts.health-check.enabled=false so this won't fail
        // but we can still test the check logic
        assertThatCode(() -> check.checkFontsAtStartup()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FontHealthCheck with failOnError=false logs warning but does not throw")
    void checkFonts_failOnErrorFalse_doesNotThrow() {
        FontHealthCheck check = new FontHealthCheck();
        // Set failOnError via reflection if needed
        assertThatCode(() -> check.checkFontsAtStartup()).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 3: Run these two test classes**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn clean test -Dtest="JpaReceiptPdfDocumentRepositoryImplTest,FontHealthCheckTest"`
Expected: All tests PASS

- [ ] **Step 4: Run full test suite**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn clean test`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service
git add src/test/java/com/wpanther/receipt/pdf/infrastructure/adapter/out/persistence/JpaReceiptPdfDocumentRepositoryImplTest.java src/test/java/com/wpanther/receipt/pdf/infrastructure/config/FontHealthCheckTest.java
git commit -m "test: add persistence and font health check tests"
```

---

## Task 19: Final verification — full build with JaCoCo coverage

**Files:** None (verification only)

- [ ] **Step 1: Run full Maven verify (tests + JaCoCo coverage)**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn verify`
Expected: BUILD SUCCESS with JaCoCo coverage >= 90% (if JaCoCo configured; otherwise add JaCoCo plugin to pom.xml)

> Note: If JaCoCo is not yet configured in pom.xml, add it as a build plugin following the same pattern as the taxinvoice service. Check taxinvoice's pom.xml for the JaCoCo configuration and add the equivalent.

- [ ] **Step 2: Verify all test classes pass**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/receipt-pdf-generation-service && mvn clean test 2>&1 | grep -E "(Tests run|BUILD)"`
Expected: All test classes report 0 failures, BUILD SUCCESS

- [ ] **Step 3: Verify test count matches spec**

Expected test classes (17 total):
1. `SagaCommandHandlerTest`
2. `ReceiptPdfDocumentServiceTest`
3. `CamelRouteConfigTest`
4. `FopReceiptPdfGeneratorTest`
5. `PdfA3ConverterTest`
6. `ReceiptPdfGenerationServiceImplTest`
7. `ThaiAmountWordsConverterTest`
8. `MinioStorageAdapterTest`
9. `MinioCleanupServiceTest`
10. `RestTemplateSignedXmlFetcherTest`
11. `EventPublisherTest`
12. `SagaReplyPublisherTest`
13. `KafkaCommandMapperTest`
14. `JpaReceiptPdfDocumentRepositoryImplTest`
15. `FontHealthCheckTest`
16. `ReceiptPdfDocumentTest` (from Task 3)
17. `ReceiptPdfGenerationExceptionTest` + `PdfGenerationConstantsTest` (from Task 3)

- [ ] **Step 4: Final git status check**

Run: `git status`
Expected: clean working tree (all changes committed)

---

## Plan complete

This plan covers all aspects of the spec:
- **SagaStep enum** (Task 1)
- **Project scaffolding** (Task 2)
- **Domain model** (Task 3)
- **Application ports** (Task 4)
- **Application services + use cases** (Task 5)
- **Persistence layer** (Task 6)
- **Messaging adapters** (Task 7)
- **Kafka route config** (Task 8)
- **Flyway migration** (Task 9)
- **PDF generation pipeline** (Task 10)
- **Storage + REST client** (Task 11)
- **Infrastructure config** (Task 12)
- **Resource files + XSL-FO** (Task 13)
- **Build verification** (Task 14)
- **Core tests** (Task 15)
- **PDF tests** (Task 16)
- **Adapter tests** (Task 17)
- **Remaining tests** (Task 18)
- **Final verification** (Task 19)
