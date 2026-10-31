package com.example.interview.gateway;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestLogging implements GlobalFilter, Ordered {
    private static final Logger LOG = LoggerFactory.getLogger(RequestLogging.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        exchange.getResponse().getHeaders().set("X-Request-Id", requestId);
        var request = exchange.getRequest().mutate().headers(headers -> headers.set("X-Request-Id", requestId)).build();
        return chain.filter(exchange.mutate().request(request).build()).doFinally(signal ->
                LOG.info("requestId={} method={} path={} status={} durationMs={} signal={}", requestId,
                        request.getMethod(), request.getPath(), exchange.getResponse().getStatusCode(),
                        (System.nanoTime() - start) / 1_000_000, signal));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
