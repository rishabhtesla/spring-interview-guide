package com.example.interview.orders;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class OrderDatabaseTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.6");
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private OrderService service;

    @BeforeEach
    void setup() {
        var dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS outbox, orders");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__orders_and_outbox.sql"))
                .execute(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        service = new OrderService(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    private OrderService.Order create(OrderService target, UUID owner, UUID key, OrderController.CreateOrder request) {
        return transaction.execute(status -> {
            try {
                return target.create(owner, key, request);
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    @Test
    void duplicateRequestCreatesOneOrderAndOneEventAndEnforcesOwnership() {
        UUID owner = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        var request = new OrderController.CreateOrder("book", 2, new BigDecimal("10.00"));
        var first = create(service, owner, key, request);
        assertThat(create(service, owner, key, request).id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> create(service, owner, key,
                new OrderController.CreateOrder("book", 3, new BigDecimal("10.00"))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.findOne(UUID.randomUUID(), first.id()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void eventSerializationFailureRollsBackOrderInsert() throws Exception {
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        when(brokenMapper.writeValueAsString(any())).thenThrow(
                new com.fasterxml.jackson.core.JsonProcessingException("serialization failed") {});
        OrderService broken = new OrderService(jdbc, brokenMapper);
        assertThatThrownBy(() -> create(broken, UUID.randomUUID(), UUID.randomUUID(),
                new OrderController.CreateOrder("book", 1, new BigDecimal("10.00"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class)).isZero();
    }
}
