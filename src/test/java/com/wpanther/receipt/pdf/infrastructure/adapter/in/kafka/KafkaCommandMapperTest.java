package com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka;

import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaCommandMapperTest {

    private final KafkaCommandMapper mapper = new KafkaCommandMapper();

    @Test
    void toProcess_returnsSameInstance() {
        KafkaReceiptProcessCommand cmd = new KafkaReceiptProcessCommand(
                "saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr", "doc-1", "RCP-001", "http://url");
        assertThat(mapper.toProcess(cmd)).isSameAs(cmd);
    }

    @Test
    void toCompensate_returnsSameInstance() {
        KafkaReceiptCompensateCommand cmd = new KafkaReceiptCompensateCommand(
                "saga-1", SagaStep.GENERATE_RECEIPT_PDF, "corr", "doc-1");
        assertThat(mapper.toCompensate(cmd)).isSameAs(cmd);
    }
}
