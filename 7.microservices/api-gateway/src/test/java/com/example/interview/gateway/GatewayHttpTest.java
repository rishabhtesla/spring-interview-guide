package com.example.interview.gateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayHttpTest {
    private static final HttpServer BACKEND = backend();
    private static volatile String forwardedAuthorization;
    @Autowired WebTestClient client;
    @MockitoBean ReactiveJwtDecoder decoder;

    private static HttpServer backend() {
        try {
            HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/orders", exchange -> {
                forwardedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
                byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String backend = "http://127.0.0.1:" + BACKEND.getAddress().getPort();
        registry.add("app.routes.auth", () -> backend);
        registry.add("app.routes.orders", () -> backend);
        registry.add("app.routes.notifications", () -> backend);
    }

    @AfterAll
    static void stop() {
        BACKEND.stop(0);
    }

    @Test
    void rejectsAnonymousAndRoutesVerifiedBearerToken() {
        client.get().uri("/orders").exchange().expectStatus().isUnauthorized();
        Jwt token = Jwt.withTokenValue("verified").header("alg", "RS256").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900))
                .claim("scope", "orders").build();
        when(decoder.decode("verified")).thenReturn(Mono.just(token));
        client.get().uri("/orders").header("Authorization", "Bearer verified")
                .exchange().expectStatus().isOk().expectBody().json("[]");
        assertThat(forwardedAuthorization).isEqualTo("Bearer verified");
    }
}
