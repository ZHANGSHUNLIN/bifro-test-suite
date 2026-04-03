package com.baidu.duhome.database.service;

import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.iot.test.suite.worker.TaskConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaskInfoMetadataService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TaskInfoMetadataServiceTest {

    @Mock
    private ReactiveMongoTemplate reactiveMongoTemplate;

    @InjectMocks
    private TaskInfoMetadataService taskInfoMetadataService;

    private TaskInfoMetadata metadata1;
    private TaskInfoMetadata metadata2;
    private static final String GROUP_ID_1 = "group-001";
    private static final String GROUP_ID_2 = "group-002";
    private static final String TASK_TYPE_CONN = "CONN";
    private static final String TASK_TYPE_PUBSUB = "PUBSUB";

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
        // given
        PageRequest pageable = PageRequest.of(0, 10);
        Flux<TaskInfoMetadata> flux = Flux.just(metadata1, metadata2);
        when(reactiveMongoTemplate.find(any(Query.class), eq(TaskInfoMetadata.class)))
                .thenReturn(flux);
        when(reactiveMongoTemplate.count(any(Query.class), eq(TaskInfoMetadata.class)))
                .thenReturn(Mono.just(2L));

        // when
        Page<TaskInfoMetadata> result = taskInfoMetadataService
                .findByFilters("Test Task", TASK_TYPE_CONN, GROUP_ID_1, pageable)
                .block();

        // then
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        verify(reactiveMongoTemplate, times(1)).find(any(Query.class), eq(TaskInfoMetadata.class));
    }

    @Test
    void testFindByFilters_withGroupFilter_shouldReturnFilteredResults() {
        // given
        PageRequest pageable = PageRequest.of(0, 10);
        Flux<TaskInfoMetadata> flux = Flux.just(metadata1, metadata2);
        when(reactiveMongoTemplate.find(any(Query.class), eq(TaskInfoMetadata.class)))
                .thenReturn(flux);
        when(reactiveMongoTemplate.count(any(Query.class), eq(TaskInfoMetadata.class)))
                .thenReturn(Mono.just(2L));

        // when
        Page<TaskInfoMetadata> result = taskInfoMetadataService
                .findByFilters(null, null, GROUP_ID_1, pageable)
                .block();

        // then
        assertNotNull(result);
        verify(reactiveMongoTemplate, times(1)).find(any(Query.class), eq(TaskInfoMetadata.class));
    }

    @Test
    void testFindByFilters_withTaskNameFilter_shouldReturnFilteredResults() {
        // given
        PageRequest pageable = PageRequest.of(0, 10);
        Flux<TaskInfoMetadata> flux = Flux.just(metadata1);
        when(reactiveMongoTemplate.find(any(Query.class), eq(TaskInfoMetadata.class)))
                .thenReturn(flux);
        when(reactiveMongoTemplate.count(any(Query.class), eq(TaskInfoMetadata.class)))
                .thenReturn(Mono.just(1L));

        // when
        Page<TaskInfoMetadata> result = taskInfoMetadataService
                .findByFilters("Test", null, null, pageable)
                .block();

        // then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(reactiveMongoTemplate, times(1)).find(any(Query.class), eq(TaskInfoMetadata.class));
    }

    @Test
    void testFindByFilters_withTaskTypeFilter_shouldReturnFilteredResults() {
        // given
        PageRequest pageable = PageRequest.of(0, 10);
        Flux<TaskInfoMetadata> flux = Flux.just(metadata2);
        when(reactiveMongoTemplate.find(any(Query.class), eq(TaskInfoMetadata.class)))
                .thenReturn(flux);
        when(reactiveMongoTemplate.count(any(Query.class), eq(TaskInfoMetadata.class)))
                .thenReturn(Mono.just(1L));

        // when
        Page<TaskInfoMetadata> result = taskInfoMetadataService
                .findByFilters(null, TASK_TYPE_PUBSUB, null, pageable)
                .block();

        // then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(reactiveMongoTemplate, times(1)).find(any(Query.class), eq(TaskInfoMetadata.class));
    }

    @Test
    void testFindByFilters_withEmptyFilters_shouldReturnAllResults() {
        // given
        PageRequest pageable = PageRequest.of(0, 10);
        Flux<TaskInfoMetadata> flux = Flux.just(metadata1, metadata2);
        when(reactiveMongoTemplate.find(any(Query.class), eq(TaskInfoMetadata.class)))
                .thenReturn(flux);
        when(reactiveMongoTemplate.count(any(Query.class), eq(TaskInfoMetadata.class)))
                .thenReturn(Mono.just(2L));

        // when
        Page<TaskInfoMetadata> result = taskInfoMetadataService
                .findByFilters(null, null, null, pageable)
                .block();

        // then
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
    }

    @Test
    void testFindByFilters_emptyResult_shouldReturnEmptyPage() {
        // given
        PageRequest pageable = PageRequest.of(0, 10);
        when(reactiveMongoTemplate.find(any(Query.class), eq(TaskInfoMetadata.class)))
                .thenReturn(Flux.empty());
        when(reactiveMongoTemplate.count(any(Query.class), eq(TaskInfoMetadata.class)))
                .thenReturn(Mono.just(0L));

        // when
        Page<TaskInfoMetadata> result = taskInfoMetadataService
                .findByFilters("NonExistent", null, null, pageable)
                .block();

        // then
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }
}
