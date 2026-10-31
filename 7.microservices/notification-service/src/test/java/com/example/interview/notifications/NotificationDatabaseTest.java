package com.example.interview.notifications;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.interview.common.OrderCreated;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class NotificationDatabaseTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.6");

    @Test
    void duplicateDeliveryPersistsOnlyOneOwnerScopedNotification() {
        var dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__notifications.sql")).execute(dataSource);
        NotificationStore store = new NotificationStore(new JdbcTemplate(dataSource));
        UUID owner = UUID.randomUUID();
        OrderCreated event = new OrderCreated(1, UUID.randomUUID(), UUID.randomUUID(), owner,
                "book", 1, new BigDecimal("10.00"), Instant.now());
        store.save(event);
        store.save(event);
        assertThat(store.find(owner)).hasSize(1);
        assertThat(store.find(UUID.randomUUID())).isEmpty();
    }
}
