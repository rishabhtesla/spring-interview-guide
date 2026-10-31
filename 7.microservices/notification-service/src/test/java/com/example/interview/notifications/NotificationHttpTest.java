package com.example.interview.notifications;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@Import(NotificationSecurity.class)
class NotificationHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean NotificationStore store;

    @Test
    void requiresScopeAndRestrictsLookupToSubject() throws Exception {
        mvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/notifications").with(jwt())).andExpect(status().isForbidden());
        UUID owner = UUID.randomUUID();
        when(store.find(owner)).thenReturn(List.of());
        mvc.perform(get("/notifications").with(jwt().jwt(jwt -> jwt.subject(owner.toString())
                .claim("scope", "notifications")))).andExpect(status().isOk());
        verify(store).find(owner);
    }
}
