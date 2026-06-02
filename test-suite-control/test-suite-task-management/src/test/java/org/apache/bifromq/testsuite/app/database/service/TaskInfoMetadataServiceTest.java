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

package org.apache.bifromq.testsuite.app.database.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class TaskInfoMetadataServiceTest {

    private static final String GROUP_ID_1 = "group-001";
    private static final String GROUP_ID_2 = "group-002";
    private static final String TASK_TYPE_CONN = "CONN";
    private static final String TASK_TYPE_PUBSUB = "PUBSUB";
    @Mock
    private ReactiveMongoTemplate reactiveMongoTemplate;
    @InjectMocks
    private TaskInfoMetadataService taskInfoMetadataService;
    private TaskInfoMetadata metadata1;
    private TaskInfoMetadata metadata2;

    @BeforeEach
    void setUp() {
        TaskConfig config1 = TaskConfig.builder()
            .taskId("task-001")
            .taskType(TaskConfig.TaskType.CONN)
            .totalClientCount(100)
            .build();

        TaskConfig config2 = TaskConfig.builder()
            .taskId("task-002")
            .taskType(TaskConfig.TaskType.PUBSUB)
            .totalClientCount(200)
            .build();

        metadata1 = TaskInfoMetadata.builder()
            .taskId("task-001")
            .taskName("Test Task 1")
            .taskType(TASK_TYPE_CONN)
            .group(GROUP_ID_1)
            .taskConfig(config1)
            .createTime(LocalDateTime.now())
            .build();

        metadata2 = TaskInfoMetadata.builder()
            .taskId("task-002")
            .taskName("Test Task 2")
            .taskType(TASK_TYPE_PUBSUB)
            .group(GROUP_ID_2)
            .taskConfig(config2)
            .createTime(LocalDateTime.now())
            .build();
    }

    @Test
    void testFindByFilters_withAllFilters_shouldReturnFilteredResults() {
        
        PageRequest pageable = PageRequest.of(0, 10);
        Flux<TaskInfoMetadata> flux = Flux.just(metadata1, metadata2);
        when(reactiveMongoTemplate.find(any(Query.class), eq(TaskInfoMetadata.class)))
            .thenReturn(flux);
        when(reactiveMongoTemplate.count(any(Query.class), eq(TaskInfoMetadata.class)))
            .thenReturn(Mono.just(2L));

        
        Page<TaskInfoMetadata> result = taskInfoMetadataService
            .findByFilters("Test Task", TASK_TYPE_CONN, GROUP_ID_1, null, pageable)
            .block();

        
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        verify(reactiveMongoTemplate, times(1)).find(any(Query.class), eq(TaskInfoMetadata.class));
    }

    @Test
    void testFindByFilters_withGroupFilter_shouldReturnFilteredResults() {
        
        PageRequest pageable = PageRequest.of(0, 10);
        Flux<TaskInfoMetadata> flux = Flux.just(metadata1, metadata2);
        when(reactiveMongoTemplate.find(any(Query.class), eq(TaskInfoMetadata.class)))
            .thenReturn(flux);
        when(reactiveMongoTemplate.count(any(Query.class), eq(TaskInfoMetadata.class)))
            .thenReturn(Mono.just(2L));

        
        Page<TaskInfoMetadata> result = taskInfoMetadataService
            .findByFilters(null, null, GROUP_ID_1, null, pageable)
            .block();

        
        assertNotNull(result);
        verify(reactiveMongoTemplate, times(1)).find(any(Query.class), eq(TaskInfoMetadata.class));
    }

    @Test
    void testFindByFilters_withTaskNameFilter_shouldReturnFilteredResults() {
        
        PageRequest pageable = PageRequest.of(0, 10);
        Flux<TaskInfoMetadata> flux = Flux.just(metadata1);
        when(reactiveMongoTemplate.find(any(Query.class), eq(TaskInfoMetadata.class)))
            .thenReturn(flux);
        when(reactiveMongoTemplate.count(any(Query.class), eq(TaskInfoMetadata.class)))
            .thenReturn(Mono.just(1L));

        
        Page<TaskInfoMetadata> result = taskInfoMetadataService
            .findByFilters("Test", null, null, null, pageable)
            .block();

        
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(reactiveMongoTemplate, times(1)).find(any(Query.class), eq(TaskInfoMetadata.class));
    }

    @Test
    void testFindByFilters_withTaskTypeFilter_shouldReturnFilteredResults() {
        
        PageRequest pageable = PageRequest.of(0, 10);
        Flux<TaskInfoMetadata> flux = Flux.just(metadata2);
        when(reactiveMongoTemplate.find(any(Query.class), eq(TaskInfoMetadata.class)))
            .thenReturn(flux);
        when(reactiveMongoTemplate.count(any(Query.class), eq(TaskInfoMetadata.class)))
            .thenReturn(Mono.just(1L));

        
        Page<TaskInfoMetadata> result = taskInfoMetadataService
            .findByFilters(null, TASK_TYPE_PUBSUB, null, null, pageable)
            .block();

        
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(reactiveMongoTemplate, times(1)).find(any(Query.class), eq(TaskInfoMetadata.class));
    }

    @Test
    void testFindByFilters_withEmptyFilters_shouldReturnAllResults() {
        
        PageRequest pageable = PageRequest.of(0, 10);
        Flux<TaskInfoMetadata> flux = Flux.just(metadata1, metadata2);
        when(reactiveMongoTemplate.find(any(Query.class), eq(TaskInfoMetadata.class)))
            .thenReturn(flux);
        when(reactiveMongoTemplate.count(any(Query.class), eq(TaskInfoMetadata.class)))
            .thenReturn(Mono.just(2L));

        
        Page<TaskInfoMetadata> result = taskInfoMetadataService
            .findByFilters(null, null, null, null, pageable)
            .block();

        
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
    }

    @Test
    void testFindByFilters_emptyResult_shouldReturnEmptyPage() {
        
        PageRequest pageable = PageRequest.of(0, 10);
        when(reactiveMongoTemplate.find(any(Query.class), eq(TaskInfoMetadata.class)))
            .thenReturn(Flux.empty());
        when(reactiveMongoTemplate.count(any(Query.class), eq(TaskInfoMetadata.class)))
            .thenReturn(Mono.just(0L));

        
        Page<TaskInfoMetadata> result = taskInfoMetadataService
            .findByFilters("NonExistent", null, null, null, pageable)
            .block();

        
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }
}
