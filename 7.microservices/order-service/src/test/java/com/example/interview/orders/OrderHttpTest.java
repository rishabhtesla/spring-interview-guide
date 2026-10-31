package com.example.interview.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class, properties = "app.jwt.issuer=interview-lab")
@Import(OrderSecurity.class)
class OrderHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderService service;
    @MockitoBean JwtDecoder decoder;

    @Test
    void requiresTokenAndOrderScope() throws Exception {
        mvc.perform(get("/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/orders").with(jwt())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void passesVerifiedSubjectToService() throws Exception {
        UUID owner = UUID.randomUUID();
        Jwt token = Jwt.withTokenValue("verified").header("alg", "RS256").subject(owner.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900))
                .claim("scope", "orders").build();
        when(decoder.decode("verified")).thenReturn(token);
        when(service.find(owner)).thenReturn(List.of());
        mvc.perform(get("/orders").header("Authorization", "Bearer verified")).andExpect(status().isOk());
        verify(service).find(owner);
    }

    @Test
    void validatesRequestAndIdempotencyKey() throws Exception {
        mvc.perform(post("/orders").with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString())
                        .claim("scope", "orders")))
                .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                .content("{\"product\":\"book\",\"quantity\":0,\"unitPrice\":1.00}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/orders").with(jwt().jwt(jwt -> jwt.claim("scope", "orders")))
                .contentType("application/json")
                .content("{\"product\":\"book\",\"quantity\":1,\"unitPrice\":1.00}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
