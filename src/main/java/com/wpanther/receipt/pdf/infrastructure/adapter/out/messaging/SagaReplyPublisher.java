package com.wpanther.receipt.pdf.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.receipt.pdf.application.port.out.SagaReplyPort;
import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaReply;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Publishes saga reply events via outbox pattern.
 * Replies are sent to orchestrator via saga.reply.receipt-pdf topic.
 */
@Component
@Slf4j
public class SagaReplyPublisher implements SagaReplyPort {

    private static final String AGGREGATE_TYPE = OutboxConstants.AGGREGATE_TYPE;

    private final String replyTopic;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public SagaReplyPublisher(
            @Value("${app.kafka.topics.saga-reply-receipt-pdf:saga.reply.receipt-pdf}") String replyTopic,
            OutboxService outboxService,
            ObjectMapper objectMapper) {
        this.replyTopic = replyTopic;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

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

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                replyTopic,
                sagaId,
                toJson(headers)
        );

        log.info("Published SUCCESS saga reply for saga {} step {}", sagaId, sagaStep);
        log.debug("SUCCESS reply pdfUrl={} pdfSize={}", pdfUrl, pdfSize);
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

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                replyTopic,
                sagaId,
                toJson(headers)
        );

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

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                replyTopic,
                sagaId,
                toJson(headers)
        );

        log.info("Published COMPENSATED saga reply for saga {} step {}", sagaId, sagaStep);
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event headers — aborting to prevent publishing without correlation headers", e);
        }
    }

    /**
     * Saga reply event for receipt PDF generation service.
     * Published to Kafka topic: saga.reply.receipt-pdf
     *
     * SUCCESS replies include pdfUrl and pdfSize so the orchestrator
     * can forward the URL to subsequent steps.
     */
    private static class ReceiptPdfReplyEvent extends SagaReply {

        private static final long serialVersionUID = 1L;

        // Additional fields included in SUCCESS replies
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

        public String getPdfUrl() {
            return pdfUrl;
        }

        public Long getPdfSize() {
            return pdfSize;
        }
    }
}
