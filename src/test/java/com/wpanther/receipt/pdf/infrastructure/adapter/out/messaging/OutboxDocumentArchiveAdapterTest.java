package com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.receipt.pdf.application.dto.event.DocumentArchiveEvent;
import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboxDocumentArchiveAdapterTest {

    private final OutboxService outbox = mock(OutboxService.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final OutboxDocumentArchiveAdapter adapter = new OutboxDocumentArchiveAdapter(outbox, mapper);

    @Test
    void publishesUnsignedPdfArchiveEvent() {
        DocumentArchiveEvent event = new DocumentArchiveEvent(
                "doc-1", "RCP-1", "RECEIPT", "UNSIGNED_PDF",
                "http://minio/bucket/x.pdf", "x.pdf", "application/pdf", 1L, "s", "c");
        when(outbox.saveWithRouting(any(), any(), any(), any(), any(), any())).thenReturn(mock(OutboxEvent.class));

        adapter.publish(event);

        ArgumentCaptor<String> aggregateType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        verify(outbox).saveWithRouting(eq(event), aggregateType.capture(), eq("doc-1"),
                topic.capture(), eq("doc-1"), any());
        assertThat(aggregateType.getValue()).isEqualTo("ReceiptPdfDocument");
        assertThat(topic.getValue()).isEqualTo("document.archive");
    }
}