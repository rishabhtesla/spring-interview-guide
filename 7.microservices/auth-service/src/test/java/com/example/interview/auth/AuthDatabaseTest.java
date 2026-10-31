package com.example.interview.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
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
class AuthDatabaseTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.6");
    private JdbcTemplate jdbc;
    private AuthController controller;

    @BeforeEach
    void setup() {
        var dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS accounts");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__accounts.sql")).execute(dataSource);
        JwtEncoder encoder = mock(JwtEncoder.class);
        when(encoder.encode(any(JwtEncoderParameters.class))).thenReturn(
                Jwt.withTokenValue("signed").header("alg", "RS256").subject("test").build());
        controller = new AuthController(jdbc, new BCryptPasswordEncoder(4), encoder, "interview-lab");
    }

    @Test
    void storesHashNormalizesEmailAndRejectsInvalidLogin() {
        var credentials = new AuthController.Credentials("Learner@example.com", "a-valid-lab-password");
        controller.register(credentials);
        String hash = jdbc.queryForObject("SELECT password_hash FROM accounts", String.class);
        assertThat(hash).isNotEqualTo(credentials.password());
        assertThat(new BCryptPasswordEncoder().matches(credentials.password(), hash)).isTrue();
        assertThat(controller.login(new AuthController.Credentials("learner@example.com", credentials.password()))
                .expiresIn()).isEqualTo(900);
        assertThatThrownBy(() -> controller.register(credentials)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.login(
                new AuthController.Credentials(credentials.email(), "wrong-but-long-password")))
                .isInstanceOf(ResponseStatusException.class);
    }
}
