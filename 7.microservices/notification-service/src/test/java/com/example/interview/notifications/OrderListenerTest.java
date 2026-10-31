package com.example.interview.notifications;

import com.example.interview.common.OrderCreated;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderListenerTest {
    @Test
    void doesNotSwallowMalformedEvents() {
        NotificationStore store = mock(NotificationStore.class);
        OrderListener listener = new OrderListener(new ObjectMapper().findAndRegisterModules(), store);
        assertThatThrownBy(() -> listener.onOrder("not-json")).isInstanceOf(JsonProcessingException.class);
        verifyNoInteractions(store);
    }

    @Test
    void propagatesDatabaseFailureSoKafkaCanRetry() {
        NotificationStore store = mock(NotificationStore.class);
        doThrow(new IllegalStateException("database unavailable")).when(store).save(any(OrderCreated.class));
        OrderListener listener = new OrderListener(new ObjectMapper().findAndRegisterModules(), store);
        assertThatThrownBy(() -> listener.onOrder("""
                {"version":1,"eventId":"11111111-1111-1111-1111-111111111111",
                 "orderId":"22222222-2222-2222-2222-222222222222","ownerId":"33333333-3333-3333-3333-333333333333",
                 "product":"book","quantity":1,"unitPrice":10.00,"occurredAt":"2026-01-01T00:00:00Z"}
                """)).isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
    }
}
