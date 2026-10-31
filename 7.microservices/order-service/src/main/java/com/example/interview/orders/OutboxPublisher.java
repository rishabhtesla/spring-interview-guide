package com.example.interview.orders;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.example.interview.common.OrderCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private record Pending(UUID eventId, UUID orderId, String payload) {}

    public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.transaction = transaction;
    }

    @Scheduled(fixedDelayString = "${app.outbox.delay-ms:2000}")
    public void publish() {
        transaction.executeWithoutResult(status -> {
            List<Pending> pending = jdbc.query("""
                    SELECT event_id, order_id, payload FROM outbox WHERE published_at IS NULL
                    ORDER BY created_at LIMIT 10 FOR UPDATE SKIP LOCKED
                    """, (row, index) -> new Pending(row.getObject("event_id", UUID.class),
                    row.getObject("order_id", UUID.class), row.getString("payload")));
            for (Pending event : pending) {
                try {
                    kafka.send(OrderCreated.TOPIC, event.orderId().toString(), event.payload())
                            .get(5, TimeUnit.SECONDS);
                    jdbc.update("UPDATE outbox SET published_at = CURRENT_TIMESTAMP WHERE event_id = ?", event.eventId());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Outbox publisher interrupted", ex);
                } catch (ExecutionException | TimeoutException ex) {
                    LOG.error("Outbox delivery failed for event {}; will retry from durable storage",
                            event.eventId(), ex);
                    // A timeout is ambiguous: Kafka may have accepted it. The consumer must deduplicate.
                    break;
                }
            }
        });
    }
}
