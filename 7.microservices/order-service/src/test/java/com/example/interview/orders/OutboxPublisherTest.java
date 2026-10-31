package com.example.interview.orders;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {
    @Test
    void pollsInsideDatabaseTransaction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback =
                    invocation.getArgument(0);
            callback.accept(mock(org.springframework.transaction.TransactionStatus.class));
            return null;
        }).when(transaction).executeWithoutResult(any());
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        new OutboxPublisher(jdbc, kafka, transaction).publish();
        verify(transaction).executeWithoutResult(any());
        verify(jdbc).query(anyString(), any(RowMapper.class));
    }

    @Test
    void failedKafkaDeliveryDoesNotMarkEventPublished() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback =
                    invocation.getArgument(0);
            callback.accept(mock(org.springframework.transaction.TransactionStatus.class));
            return null;
        }).when(transaction).executeWithoutResult(any());
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("event_id", UUID.class)).thenReturn(UUID.randomUUID());
        when(row.getObject("order_id", UUID.class)).thenReturn(UUID.randomUUID());
        when(row.getString("payload")).thenReturn("{}");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        new OutboxPublisher(jdbc, kafka, transaction).publish();
        verify(jdbc, never()).update(anyString(), any(UUID.class));
    }
}
