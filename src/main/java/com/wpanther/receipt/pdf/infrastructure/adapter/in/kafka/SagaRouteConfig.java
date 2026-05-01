package com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.receipt.pdf.application.port.in.CompensateReceiptPdfUseCase;
import com.wpanther.receipt.pdf.application.port.in.ProcessReceiptPdfUseCase;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.CompensateReceiptPdfCommand;
import com.wpanther.receipt.pdf.infrastructure.adapter.in.kafka.dto.ProcessReceiptPdfCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Camel route configuration for saga command and compensation handling.
 * Consumes messages from Kafka topics and delegates to use case interfaces
 * with plain field parameters.
 */
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
                            if (body instanceof ProcessReceiptPdfCommand cmd) {
                                log.error("DLQ: notifying orchestrator of retry exhaustion for saga {} document {}",
                                        cmd.getSagaId(), cmd.getDocumentNumber());
                                sagaCommandHandler.publishOrchestrationFailure(
                                        cmd.getSagaId(), cmd.getSagaStep(), cmd.getCorrelationId(), cause);
                            } else if (body instanceof CompensateReceiptPdfCommand cmd) {
                                log.error("DLQ: notifying orchestrator of compensation retry exhaustion for saga {} document {}",
                                        cmd.getSagaId(), cmd.getDocumentId());
                                sagaCommandHandler.publishCompensationOrchestrationFailure(
                                        cmd.getSagaId(), cmd.getSagaStep(), cmd.getCorrelationId(), cause);
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
                .unmarshal().json(JsonLibrary.Jackson, ProcessReceiptPdfCommand.class)
                .process(exchange -> {
                        ProcessReceiptPdfCommand cmd =
                                exchange.getIn().getBody(ProcessReceiptPdfCommand.class);
                        log.info("Processing saga command for saga: {}, document: {}",
                                        cmd.getSagaId(), cmd.getDocumentNumber());
                        processUseCase.process(cmd.getDocumentId(), cmd.getDocumentNumber(),
                                cmd.getSignedXmlUrl(), cmd.getSagaId(), cmd.getSagaStep(), cmd.getCorrelationId());
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
                .unmarshal().json(JsonLibrary.Jackson, CompensateReceiptPdfCommand.class)
                .process(exchange -> {
                        CompensateReceiptPdfCommand cmd =
                                exchange.getIn().getBody(CompensateReceiptPdfCommand.class);
                        log.info("Processing compensation for saga: {}, document: {}",
                                        cmd.getSagaId(), cmd.getDocumentId());
                        compensateUseCase.compensate(cmd.getDocumentId(), cmd.getSagaId(),
                                cmd.getSagaStep(), cmd.getCorrelationId());
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