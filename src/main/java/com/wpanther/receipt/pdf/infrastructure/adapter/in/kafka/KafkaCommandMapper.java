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
