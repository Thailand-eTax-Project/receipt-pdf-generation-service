package com.wpanther.receipt.pdf.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for OutboxEvent from saga-commons.
 * Stores integration events in the outbox table for reliable event publishing.
 * Supports both polling-based and Debezium CDC publishing patterns.
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status", columnList = "status"),
    @Index(name = "idx_outbox_created", columnList = "created_at"),
    @Index(name = "idx_outbox_aggregate", columnList = "aggregate_id, aggregate_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEventEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // Debezium CDC Support
    @Column(name = "topic", length = 255)
    private String topic;

    @Column(name = "partition_key", length = 255)
    private String partitionKey;

    @Column(name = "headers", columnDefinition = "TEXT")
    private String headers;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = OutboxStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    /**
     * Convert domain OutboxEvent to JPA entity.
     */
    public static OutboxEventEntity fromDomain(com.wpanther.saga.domain.outbox.OutboxEvent event) {
        return OutboxEventEntity.builder()
                .id(event.getId())
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .eventType(event.getEventType())
                .payload(event.getPayload())
                .createdAt(event.getCreatedAt())
                .publishedAt(event.getPublishedAt())
                .status(event.getStatus())
                .retryCount(event.getRetryCount())
                .errorMessage(event.getErrorMessage())
                .topic(event.getTopic())
                .partitionKey(event.getPartitionKey())
                .headers(event.getHeaders())
                .build();
    }

    /**
     * Convert JPA entity to domain OutboxEvent.
     */
    public com.wpanther.saga.domain.outbox.OutboxEvent toDomain() {
        return com.wpanther.saga.domain.outbox.OutboxEvent.builder()
                .id(this.id)
                .aggregateType(this.aggregateType)
                .aggregateId(this.aggregateId)
                .eventType(this.eventType)
                .payload(this.payload)
                .createdAt(this.createdAt)
                .publishedAt(this.publishedAt)
                .status(this.status)
                .retryCount(this.retryCount)
                .errorMessage(this.errorMessage)
                .topic(this.topic)
                .partitionKey(this.partitionKey)
                .headers(this.headers)
                .build();
    }
}
