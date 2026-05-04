package com.wpanther.receipt.pdf.application.port.out;

import com.wpanther.receipt.pdf.application.dto.event.DocumentArchiveEvent;

public interface DocumentArchivePort {
    void publish(DocumentArchiveEvent event);
}