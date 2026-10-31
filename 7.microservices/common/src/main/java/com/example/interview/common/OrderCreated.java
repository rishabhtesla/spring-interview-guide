package com.example.interview.common;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCreated(int version, UUID eventId, UUID orderId, UUID ownerId,
                           String product, int quantity, BigDecimal unitPrice, Instant occurredAt) {
    public static final String TOPIC = "orders.created.v1";
    public static final String DLT = TOPIC + ".DLT";

    public void validate() {
        if (version != 1 || eventId == null || orderId == null || ownerId == null
                || product == null || product.isBlank() || product.length() > 120
                || quantity < 1 || quantity > 100 || unitPrice == null
                || unitPrice.signum() <= 0 || unitPrice.scale() > 2 || occurredAt == null) {
            throw new IllegalArgumentException("Invalid or unsupported OrderCreated event");
        }
    }
}
