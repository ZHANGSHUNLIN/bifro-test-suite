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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.bifromq.testsuite.audit.domain.AuditLog;
import org.apache.bifromq.testsuite.audit.infrastructure.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ReactiveMongoTemplate mongoTemplate;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void queryUsesUnpagedCountQuery() {
        when(mongoTemplate.find(org.mockito.ArgumentMatchers.any(Query.class), eq(AuditLog.class)))
            .thenReturn(Flux.just(AuditLog.builder().id("log-1").build()));
        when(mongoTemplate.count(org.mockito.ArgumentMatchers.any(Query.class), eq(AuditLog.class)))
            .thenReturn(Mono.just(42L));

        StepVerifier.create(auditLogService.query(null, null, null, null, null, null, 2, 10))
            .assertNext(page -> {
                assertThat(page.getContent()).hasSize(1);
                assertThat(page.getTotalElements()).isEqualTo(42);
                assertThat(page.getTotalPages()).isEqualTo(5);
                assertThat(page.getNumber()).isEqualTo(1);
            })
            .verifyComplete();

        ArgumentCaptor<Query> findQueryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Query> countQueryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(findQueryCaptor.capture(), eq(AuditLog.class));
        verify(mongoTemplate).count(countQueryCaptor.capture(), eq(AuditLog.class));

        Query findQuery = findQueryCaptor.getValue();
        assertThat(findQuery.getSkip()).isEqualTo(10);
        assertThat(findQuery.getLimit()).isEqualTo(10);
        assertThat(findQuery.isSorted()).isTrue();

        Query countQuery = countQueryCaptor.getValue();
        assertThat(countQuery.getSkip()).isZero();
        assertThat(countQuery.isLimited()).isFalse();
        assertThat(countQuery.isSorted()).isFalse();
    }
}
