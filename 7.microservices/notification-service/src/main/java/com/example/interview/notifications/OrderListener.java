package com.example.interview.notifications;

import com.example.interview.common.OrderCreated;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderListener {
    private final ObjectMapper mapper;
    private final NotificationStore store;

    public OrderListener(ObjectMapper mapper, NotificationStore store) {
        this.mapper = mapper;
        this.store = store;
    }

    @KafkaListener(topics = OrderCreated.TOPIC)
    public void onOrder(String payload) throws JsonProcessingException {
        // Do not catch-and-ignore failures: the container must retry or route to the DLT.
        store.save(mapper.readValue(payload, OrderCreated.class));
    }
}
