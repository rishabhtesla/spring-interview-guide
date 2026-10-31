package com.example.interview.notifications;

import java.util.List;
import java.util.UUID;

import com.example.interview.common.OrderCreated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationStore {
    private final JdbcTemplate jdbc;

    public NotificationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Notification(UUID eventId, UUID orderId, String message) {}

    @Transactional
    public void save(OrderCreated event) {
        event.validate();
        // The event ID is the deduplication key, persisted atomically with the notification.
        jdbc.update("""
                INSERT INTO notifications(event_id, order_id, owner_id, message)
                VALUES (?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING
                """, event.eventId(), event.orderId(), event.ownerId(),
                "Order accepted: " + event.quantity() + " x " + event.product());
    }

    public List<Notification> find(UUID owner) {
        return jdbc.query("""
                SELECT event_id, order_id, message FROM notifications
                WHERE owner_id = ? ORDER BY created_at DESC LIMIT 100
                """, (row, index) -> new Notification(row.getObject("event_id", UUID.class),
                row.getObject("order_id", UUID.class), row.getString("message")), owner);
    }
}
