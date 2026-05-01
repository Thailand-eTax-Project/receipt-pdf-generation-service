# Receipt PDF Generation Service — Naming Convention Alignment

**Date:** 2026-05-01
**Author:** Claude Code
**Status:** Draft

## Context

After the layer separation refactor, `receipt-pdf-generation-service` follows the hexagonal architecture pattern. However, two DTO command class names are inconsistent with the reference service `invoice-pdf-generation-service`.

## Problem Statement

Two DTO command classes don't follow the `{Action}{DocumentType}Command` naming convention used in `invoice-pdf-generation-service`:

| Current (incorrect) | Correct (matching invoice pattern) |
|---------------------|-------------------------------------|
| `ReceiptProcessCommand` | `ProcessReceiptPdfCommand` |
| `ReceiptCompensateCommand` | `CompensateReceiptPdfCommand` |

The reference service `invoice-pdf-generation-service` uses:
- `ProcessInvoicePdfCommand`
- `CompensateInvoicePdfCommand`

## Files to Rename

### 1. `src/main/java/.../infrastructure/adapter/in/kafka/dto/ReceiptProcessCommand.java`
- Rename class: `ReceiptProcessCommand` → `ProcessReceiptPdfCommand`
- Update all references in `SagaRouteConfig.java`

### 2. `src/main/java/.../infrastructure/adapter/in/kafka/dto/ReceiptCompensateCommand.java`
- Rename class: `ReceiptCompensateCommand` → `CompensateReceiptPdfCommand`
- Update all references in `SagaRouteConfig.java`

## Already Consistent (No Changes Needed)

These classes already follow the correct naming convention:
- `ProcessReceiptPdfUseCase` ✓
- `CompensateReceiptPdfUseCase` ✓
- `ReceiptPdfDocumentService` ✓
- `ReceiptPdfGenerationService` ✓
- `ReceiptPdfGenerationException` ✓
- `ReceiptPdfGeneratedEvent` ✓

## What Changes

- 2 files renamed
- 2 class names updated
- References in `SagaRouteConfig.java` updated
- Test classes that reference these commands updated

## What Does NOT Change

- Package structure (stays in `infrastructure/adapter/in/kafka/dto/`)
- Internal class structure or methods
- Business logic
- `ReceiptPdfReplyEvent` (private inner class, not visible externally)

## Testing

After rename:
1. `mvn compile` — verify no compilation errors
2. `mvn test` — verify all 101 tests pass

Tests that may need updates:
- `CamelRouteConfigTest.java` — uses DTO class names in assertions
- Any test that constructs or references the command classes directly