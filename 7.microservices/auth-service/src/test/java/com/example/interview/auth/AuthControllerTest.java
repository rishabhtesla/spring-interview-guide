package com.example.interview.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        AuthController controller = new AuthController(jdbc, new BCryptPasswordEncoder(4),
                mock(JwtEncoder.class), "interview-lab");
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void rejectsInvalidRegistrationBeforeDatabaseAccess() throws Exception {
        mvc.perform(post("/auth/register").contentType("application/json")
                .content("{\"email\":\"not-an-email\",\"password\":\"short\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsPasswordsBeyondBcryptByteLimit() throws Exception {
        String password = "é".repeat(40);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(80);
        mvc.perform(post("/auth/register").contentType("application/json")
                .content("{\"email\":\"learner@example.com\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }

    @Test
    void reportsDuplicateAccountAsConflict() throws Exception {
        when(jdbc.update(anyString(), any(UUID.class), anyString(), anyString())).thenReturn(0);
        mvc.perform(post("/auth/register").contentType("application/json")
                .content("{\"email\":\"learner@example.com\",\"password\":\"a-valid-lab-password\"}"))
                .andExpect(status().isConflict());
    }
}
