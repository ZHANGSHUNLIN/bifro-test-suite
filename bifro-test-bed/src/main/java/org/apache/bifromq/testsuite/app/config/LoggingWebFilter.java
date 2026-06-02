/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.app.config;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Slf4j
@Configuration
@ConditionalOnControlPlane
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
        log.trace("[{}] >>> {} {} from {}", requestId, method, url, clientIp);
        return chain.filter(exchange)
            .contextWrite(Context.of(REQUEST_ID, requestId, START_TIME, startTime))
            .doOnSuccess(aVoid -> {

                long durationMs = Duration.between(startTime, Instant.now()).toMillis();
                int status = exchange.getResponse().getStatusCode() != null ?
                    exchange.getResponse().getStatusCode().value() : 200;
                log.trace("[{}] <<< {} {} ({} ms)", requestId, status, url, durationMs);
            })
            .doOnError(throwable -> {

                long durationMs = Duration.between(startTime, Instant.now()).toMillis();
                log.warn("[{}] !!! ERROR {} {} ({} ms) - {}", requestId, method, url, durationMs,
                    throwable.getMessage(), throwable);
            });
    }

    private String getClientIpAddress(ServerHttpRequest request) {
        var remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
