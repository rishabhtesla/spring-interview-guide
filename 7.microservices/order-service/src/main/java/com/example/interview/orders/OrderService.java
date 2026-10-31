package com.example.interview.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.interview.common.OrderCreated;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OrderService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record Order(UUID id, UUID ownerId, String product, int quantity, BigDecimal unitPrice) {}

    @Transactional(rollbackFor = JsonProcessingException.class)
    public Order create(UUID owner, UUID requestKey, OrderController.CreateOrder request) throws JsonProcessingException {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO orders(id, owner_id, request_key, product, quantity, unit_price)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (owner_id, request_key) DO NOTHING
                """, id, owner, requestKey, request.product(), request.quantity(), request.unitPrice());
        Order order = jdbc.query("""
                SELECT id, owner_id, product, quantity, unit_price FROM orders
                WHERE owner_id = ? AND request_key = ?
                """, (row, index) -> new Order(row.getObject("id", UUID.class), row.getObject("owner_id", UUID.class),
                row.getString("product"), row.getInt("quantity"), row.getBigDecimal("unit_price")), owner, requestKey)
                .getFirst();
        if (!order.product().equals(request.product()) || order.quantity() != request.quantity()
                || order.unitPrice().compareTo(request.unitPrice()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used for different input");
        }
        if (inserted == 1) {
            UUID eventId = UUID.randomUUID();
            OrderCreated event = new OrderCreated(1, eventId, id, owner, request.product(),
                    request.quantity(), request.unitPrice(), Instant.now());
            // Order + event intent commit together. Kafka is deliberately not part of this transaction.
            jdbc.update("INSERT INTO outbox(event_id, order_id, payload) VALUES (?, ?, ?)",
                    eventId, id, mapper.writeValueAsString(event));
        }
        return order;
    }

    public List<Order> find(UUID owner) {
        return jdbc.query("""
                SELECT id, owner_id, product, quantity, unit_price FROM orders
                WHERE owner_id = ? ORDER BY created_at DESC LIMIT 100
                """, (row, index) -> new Order(row.getObject("id", UUID.class), row.getObject("owner_id", UUID.class),
                row.getString("product"), row.getInt("quantity"), row.getBigDecimal("unit_price")), owner);
    }

    public Order findOne(UUID owner, UUID id) {
        List<Order> orders = jdbc.query("""
                SELECT id, owner_id, product, quantity, unit_price FROM orders
                WHERE owner_id = ? AND id = ?
                """, (row, index) -> new Order(row.getObject("id", UUID.class), row.getObject("owner_id", UUID.class),
                row.getString("product"), row.getInt("quantity"), row.getBigDecimal("unit_price")), owner, id);
        if (orders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return orders.getFirst();
    }
}
