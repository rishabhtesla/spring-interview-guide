package com.example.interview.notifications;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {
    private final NotificationStore store;

    public NotificationController(NotificationStore store) {
        this.store = store;
    }

    @GetMapping("/notifications")
    public List<NotificationStore.Notification> list(@AuthenticationPrincipal Jwt jwt) {
        return store.find(UUID.fromString(jwt.getSubject()));
    }
}
