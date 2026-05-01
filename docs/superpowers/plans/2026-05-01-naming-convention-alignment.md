# Naming Convention Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename 2 DTO command classes in `receipt-pdf-generation-service` to follow the `{Action}{DocumentType}Command` pattern used in `invoice-pdf-generation-service`.

**Architecture:** Simple file rename + class rename + update all references. No architectural changes.

**Tech Stack:** Java 21, Maven, JUnit 5

---

### Task 1: Rename `ReceiptProcessCommand` → `ProcessReceiptPdfCommand`

**Files:**
- Rename: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/dto/ReceiptProcessCommand.java` → `ProcessReceiptPdfCommand.java`
- Modify: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/SagaRouteConfig.java` (update class references)
- Modify: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/SagaCommandHandler.java` (update imports)
- Modify: `src/test/java/com/wpanther/receipt/pdf/infrastructure/config/CamelRouteConfigTest.java` (update references)
- Modify: `src/test/java/com/wpanther/receipt/pdf/application/service/SagaCommandHandlerTest.java` (update imports)

- [ ] **Step 1: Rename file**

```bash
mv src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/dto/ReceiptProcessCommand.java \
   src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/dto/ProcessReceiptPdfCommand.java
```

- [ ] **Step 2: Update class name inside file**

Change `public class ReceiptProcessCommand` → `public class ProcessReceiptPdfCommand`

- [ ] **Step 3: Update imports in SagaRouteConfig.java**

Change:
```java
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.ReceiptProcessCommand;
```
→ `ProcessReceiptPdfCommand`

- [ ] **Step 4: Update instanceof checks in SagaRouteConfig.java**

Change `body instanceof ReceiptProcessCommand cmd` → `body instanceof ProcessReceiptPdfCommand cmd`

- [ ] **Step 5: Update imports in SagaCommandHandler.java**

Change:
```java
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.ReceiptProcessCommand;
```
→ `ProcessReceiptPdfCommand`

- [ ] **Step 6: Update references in CamelRouteConfigTest.java**

Change all `ReceiptProcessCommand` → `ProcessReceiptPdfCommand` in imports and usage

- [ ] **Step 7: Update references in SagaCommandHandlerTest.java**

Change all `ReceiptProcessCommand` → `ProcessReceiptPdfCommand` in imports and usage

- [ ] **Step 8: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "refactor: rename ReceiptProcessCommand to ProcessReceiptPdfCommand"
```

---

### Task 2: Rename `ReceiptCompensateCommand` → `CompensateReceiptPdfCommand`

**Files:**
- Rename: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/dto/ReceiptCompensateCommand.java` → `CompensateReceiptPdfCommand.java`
- Modify: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/SagaRouteConfig.java`
- Modify: `src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/SagaCommandHandler.java`
- Modify: `src/test/java/com/wpanther/receipt/pdf/infrastructure/config/CamelRouteConfigTest.java`
- Modify: `src/test/java/com/wpanther/receipt/pdf/application/service/SagaCommandHandlerTest.java`

- [ ] **Step 1: Rename file**

```bash
mv src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/dto/ReceiptCompensateCommand.java \
   src/main/java/com/wpanther/receipt/pdf/infrastructure/adapter/in/kafka/dto/CompensateReceiptPdfCommand.java
```

- [ ] **Step 2: Update class name inside file**

Change `public class ReceiptCompensateCommand` → `public class CompensateReceiptPdfCommand`

- [ ] **Step 3: Update imports in SagaRouteConfig.java**

Change:
```java
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.ReceiptCompensateCommand;
```
→ `CompensateReceiptPdfCommand`

- [ ] **Step 4: Update instanceof checks in SagaRouteConfig.java**

Change `body instanceof ReceiptCompensateCommand cmd` → `body instanceof CompensateReceiptPdfCommand cmd`

- [ ] **Step 5: Update imports in SagaCommandHandler.java**

Change:
```java
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.ReceiptCompensateCommand;
```
→ `CompensateReceiptPdfCommand`

- [ ] **Step 6: Update references in CamelRouteConfigTest.java**

Change all `ReceiptCompensateCommand` → `CompensateReceiptPdfCommand` in imports and usage

- [ ] **Step 7: Update references in SagaCommandHandlerTest.java**

Change all `ReceiptCompensateCommand` → `CompensateReceiptPdfCommand` in imports and usage

- [ ] **Step 8: Verify compilation and tests**

```bash
mvn clean test -q
```
Expected: BUILD SUCCESS, 101 tests pass

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "refactor: rename ReceiptCompensateCommand to CompensateReceiptPdfCommand"
```

---

### Verification

After all tasks:
```bash
mvn clean test
```
Expected: BUILD SUCCESS, all 101 tests pass

---

**Plan complete and saved to `docs/superpowers/plans/2026-05-01-naming-convention-alignment.md`.**

Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?