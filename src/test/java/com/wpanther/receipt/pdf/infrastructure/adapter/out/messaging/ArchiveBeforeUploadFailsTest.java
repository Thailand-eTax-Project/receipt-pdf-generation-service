package com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.receipt.pdf.application.dto.event.DocumentArchiveEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ArchiveBeforeUploadFailsTest {

    private final OutboxService outbox = mock(OutboxService.class);
    private final OutboxDocumentArchiveAdapter adapter =
            new OutboxDocumentArchiveAdapter(outbox, new ObjectMapper());

    @Test
    void rejectsEventWithNullSourceUrl() {
        DocumentArchiveEvent event = new DocumentArchiveEvent(
                "doc-1", "RCP-001", "RECEIPT", "UNSIGNED_PDF",
                null, "f.pdf", "application/pdf", 100L, "s", "c");

        assertThatThrownBy(() -> adapter.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sourceUrl");
        verify(outbox, never()).saveWithRouting(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsEventWithBlankSourceUrl() {
        DocumentArchiveEvent event = new DocumentArchiveEvent(
                "doc-1", "RCP-001", "RECEIPT", "UNSIGNED_PDF",
                "   ", "f.pdf", "application/pdf", 100L, "s", "c");
        assertThatThrownBy(() -> adapter.publish(event)).isInstanceOf(IllegalStateException.class);
    }
}