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

package org.apache.bifromq.testsuite.audit.application;

import org.apache.bifromq.testsuite.audit.domain.AuditLog;
import org.apache.bifromq.testsuite.audit.infrastructure.AuditLogRepository;
import org.apache.bifromq.testsuite.web.PageInfo;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    public Mono<Void> record(ServerWebExchange exchange,
                             String action,
                             String resourceType,
                             String resourceId,
                             boolean success,
                             String message) {
        return record(exchange, action, resourceType, resourceId, success, message, Map.of());
    }

    public Mono<Void> record(ServerWebExchange exchange,
                             String action,
                             String resourceType,
                             String resourceId,
                             boolean success,
                             String message,
                             Map<String, Object> metadata) {
        return username(exchange)
            .flatMap(username -> auditLogRepository.save(AuditLog.builder()
                .username(username)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .clientIp(clientIp(exchange.getRequest()))
                .userAgent(exchange.getRequest().getHeaders().getFirst("User-Agent"))
                .requestId(exchange.getRequest().getId())
                .success(success)
                .message(message)
                .metadata(sanitizeMetadata(metadata))
                .createdAt(Instant.now())
                .build()))
            .doOnError(error -> log.warn("Failed to save audit log: {}", error.getMessage()))
            .onErrorResume(error -> Mono.empty())
            .then();
    }

    public Mono<PageInfo<AuditLog>> query(String username,
                                          String action,
                                          String resourceType,
                                          Boolean success,
                                          Instant startTime,
                                          Instant endTime,
                                          int pageNum,
                                          int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        Criteria criteria = criteria(username, action, resourceType, success, startTime, endTime);
        Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "createdAt"));
        Query countQuery = new Query(criteria);
        Query pageQuery = query.skip((long) (safePageNum - 1) * safePageSize).limit(safePageSize);
        return Mono.zip(
                mongoTemplate.find(pageQuery, AuditLog.class).collectList(),
                mongoTemplate.count(countQuery, AuditLog.class))
            .map(tuple -> pageInfo(tuple.getT1(), tuple.getT2(), safePageNum, safePageSize));
    }

    private Criteria criteria(String username,
                              String action,
                              String resourceType,
                              Boolean success,
                              Instant startTime,
                              Instant endTime) {
        Criteria criteria = new Criteria();
        if (username != null && !username.isBlank()) {
            criteria.and("username").is(username.trim());
        }
        if (action != null && !action.isBlank()) {
            criteria.and("action").is(action.trim());
        }
        if (resourceType != null && !resourceType.isBlank()) {
            criteria.and("resourceType").is(resourceType.trim());
        }
        if (success != null) {
            criteria.and("success").is(success);
        }
        if (startTime != null || endTime != null) {
            Criteria timeCriteria = criteria.and("createdAt");
            if (startTime != null) {
                timeCriteria.gte(startTime);
            }
            if (endTime != null) {
                timeCriteria.lte(endTime);
            }
        }
        return criteria;
    }

    private Mono<String> username(ServerWebExchange exchange) {
        return exchange.getPrincipal()
            .cast(Authentication.class)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .defaultIfEmpty("anonymous");
    }

    private String clientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress == null ? "unknown" : remoteAddress.getAddress().getHostAddress();
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String lowerKey = key.toLowerCase();
            if (!isSensitiveKey(lowerKey)) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private boolean isSensitiveKey(String lowerKey) {
        return lowerKey.contains("password")
            || lowerKey.contains("token")
            || lowerKey.contains("cert")
            || lowerKey.contains("key")
            || lowerKey.contains("secret")
            || lowerKey.contains("credential");
    }

    private PageInfo<AuditLog> pageInfo(java.util.List<AuditLog> content, long total, int pageNum, int pageSize) {
        PageInfo<AuditLog> pageInfo = new PageInfo<>();
        pageInfo.setContent(content);
        pageInfo.setTotalElements(total);
        pageInfo.setTotalPages((int) Math.ceil((double) total / pageSize));
        pageInfo.setSize(pageSize);
        pageInfo.setNumber(pageNum - 1);
        pageInfo.setNumberOfElements(content.size());
        pageInfo.setFirst(pageNum == 1);
        pageInfo.setLast((long) pageNum * pageSize >= total);
        return pageInfo;
    }
}
