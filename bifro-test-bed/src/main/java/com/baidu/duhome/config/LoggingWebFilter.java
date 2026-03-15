package com.baidu.duhome.config;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Configuration
public class LoggingWebFilter implements WebFilter {

    private static final String REQUEST_ID = "requestId";
    private static final String START_TIME = "startTime";

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String url = request.getURI().toString();
        String clientIp = getClientIpAddress(request);

        // 记录请求开始
        log.debug("[{}] >>> {} {} from {}", requestId, method, url, clientIp);

        // 将请求 ID 和开始时间放入 Context，供下游使用（可选）
        return chain.filter(exchange)
                .contextWrite(Context.of(REQUEST_ID, requestId, START_TIME, startTime))
                .doOnSuccess(aVoid -> {
                    // 记录响应成功
                    long durationMs = Duration.between(startTime, Instant.now()).toMillis();
                    int status = exchange.getResponse().getStatusCode() != null ?
                            exchange.getResponse().getStatusCode().value() : 200;
                    log.debug("[{}] <<< {} {} ({} ms)", requestId, status, url, durationMs);
                })
                .doOnError(throwable -> {
                    // 记录响应异常
                    long durationMs = Duration.between(startTime, Instant. now()).toMillis();
                    log.warn("[{}] !!! ERROR {} {} ({} ms) - {}", requestId, method, url, durationMs, throwable.getMessage());
                });
    }

    private String getClientIpAddress(ServerHttpRequest request) {
        var remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}