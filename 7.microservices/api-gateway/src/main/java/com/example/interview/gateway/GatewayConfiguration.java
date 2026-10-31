package com.example.interview.gateway;

import java.io.IOException;

import com.example.interview.common.JwtSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Configuration
public class GatewayConfiguration {
    @Bean
    ReactiveJwtDecoder decoder(@Value("${app.jwt.public-key}") Resource key,
                               @Value("${app.jwt.issuer}") String issuer) throws IOException {
        var decoder = JwtSupport.decoder(key, issuer);
        return token -> Mono.fromCallable(() -> decoder.decode(token)).subscribeOn(Schedulers.boundedElastic());
    }

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http.csrf(csrf -> csrf.disable())
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth -> auth
                        .pathMatchers("/auth/register", "/auth/login", "/actuator/health").permitAll()
                        .pathMatchers("/orders", "/orders/**").hasAuthority("SCOPE_orders")
                        .pathMatchers("/notifications").hasAuthority("SCOPE_notifications")
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {})).build();
    }

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder,
                        @Value("${app.routes.auth}") String auth,
                        @Value("${app.routes.orders}") String orders,
                        @Value("${app.routes.notifications}") String notifications) {
        // Gateway preserves Authorization; each resource service independently verifies the JWT.
        return builder.routes()
                .route("auth", route -> route.path("/auth/register", "/auth/login").uri(auth))
                .route("orders", route -> route.path("/orders", "/orders/**").uri(orders))
                .route("notifications", route -> route.path("/notifications").uri(notifications))
                .build();
    }
}
