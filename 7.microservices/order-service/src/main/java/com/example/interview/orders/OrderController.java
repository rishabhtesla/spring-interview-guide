package com.example.interview.orders;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    public record CreateOrder(@NotBlank @Size(max = 120) String product,
                              @Min(1) @Max(100) int quantity,
                              @NotNull @DecimalMin("0.01") @DecimalMax("999999.99")
                              @Digits(integer = 6, fraction = 2) BigDecimal unitPrice) {}

    @PostMapping("/orders")
    public ResponseEntity<OrderService.Order> create(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") UUID requestKey,
            @Valid @RequestBody CreateOrder request) throws JsonProcessingException {
        // Never accept ownerId from the request: ownership comes from the verified token.
        OrderService.Order order = service.create(UUID.fromString(jwt.getSubject()), requestKey, request);
        return ResponseEntity.created(URI.create("/orders/" + order.id())).body(order);
    }

    @GetMapping("/orders")
    public List<OrderService.Order> list(@AuthenticationPrincipal Jwt jwt) {
        return service.find(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/orders/{id}")
    public OrderService.Order get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.findOne(UUID.fromString(jwt.getSubject()), id);
    }
}
