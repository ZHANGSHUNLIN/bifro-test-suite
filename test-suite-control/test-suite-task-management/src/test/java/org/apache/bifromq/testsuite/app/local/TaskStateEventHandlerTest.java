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

package org.apache.bifromq.testsuite.app.local;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.pojo.TaskMetricsSnapshot;
import org.apache.bifromq.testsuite.app.database.pojo.TaskStateHistory;
import org.apache.bifromq.testsuite.app.database.repository.NodeTaskRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskStateHistoryRepository;
import org.apache.bifromq.testsuite.app.database.service.TaskMetricsSnapshotService;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.pojo.TaskStateChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class TaskStateEventHandlerTest {

    private static final String TASK_ID = "task-1";
    private static final String NODE_ID = "node-1";

    @Mock
    private Vertx vertx;
    @Mock
    private EventBus eventBus;
    @Mock
    private TaskStateHistoryRepository taskStateHistoryRepository;
    @Mock
    private NodeTaskRepository nodeTaskRepository;
    @Mock
    private TaskInfoMetadataRepository taskInfoMetadataRepository;
    @Mock
    private TaskMetricsSnapshotService taskMetricsSnapshotService;
    @InjectMocks
    private TaskStateEventHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(vertx.eventBus()).thenReturn(eventBus);
        lenient().when(eventBus.consumer(anyString(), any())).thenAnswer(inv -> mock(MessageConsumer.class));
        lenient().when(vertx.executeBlocking(any(java.util.concurrent.Callable.class)))
            .thenAnswer(inv -> {
                java.util.concurrent.Callable<?> callable = inv.getArgument(0);
                callable.call();
                return Future.succeededFuture();
            });
        lenient().when(taskStateHistoryRepository.save(any(TaskStateHistory.class)))
            .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void handleStateChange_terminalNodeStageShouldSaveMetricsSnapshot() throws Exception {
        Instant startAt = Instant.parse("2026-06-02T08:43:47Z");
        Instant finishedAt = Instant.parse("2026-06-02T08:44:59Z");
        NodeTask nodeTask = nodeTask(TaskStage.SHUTTING);
        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskName("test task")
            .taskConfig(TaskConfig.builder().taskId(TASK_ID).taskWorkStage(TaskStage.SHUTTING).build())
            .build();

        when(nodeTaskRepository.findByTaskIdAndNodeId(TASK_ID, NODE_ID)).thenReturn(Mono.just(nodeTask));
        when(nodeTaskRepository.save(any(NodeTask.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));
        when(taskStateHistoryRepository.findByTaskIdAndNodeIdOrderByTimestampDesc(TASK_ID, NODE_ID))
            .thenReturn(Flux.just(history(startAt), history(finishedAt)));
        when(taskMetricsSnapshotService.collectAndSaveNodeSnapshot(
            eq(TASK_ID),
            eq("test task"),
            eq(TaskStage.SHUTDOWN.name()),
            eq(NODE_ID),
            eq("node-name"),
            any(LocalDateTime.class),
            any(LocalDateTime.class)
        )).thenReturn(Mono.just(TaskMetricsSnapshot.builder().taskId(TASK_ID).build()));
        when(nodeTaskRepository.findAllByTaskId(TASK_ID)).thenReturn(Flux.just(nodeTask));
        when(taskInfoMetadataRepository.updateStageById(eq(TASK_ID), eq(TaskStage.SHUTDOWN.name()), any(Instant.class)))
            .thenReturn(Mono.empty());

        invokeHandleStateChange(TaskStateChangeEvent.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .nodeName("node-name")
            .fromStage(TaskStage.SHUTTING)
            .toStage(TaskStage.SHUTDOWN)
            .triggerEvent(TaskEvent.SHUTDOWN)
            .timestamp(finishedAt)
            .eventSeq(1)
            .build());

        verify(taskMetricsSnapshotService).collectAndSaveNodeSnapshot(
            eq(TASK_ID),
            eq("test task"),
            eq(TaskStage.SHUTDOWN.name()),
            eq(NODE_ID),
            eq("node-name"),
            any(LocalDateTime.class),
            any(LocalDateTime.class)
        );
    }

    @Test
    void handleStateChange_nonTerminalStageShouldNotSaveMetricsSnapshot() throws Exception {
        NodeTask nodeTask = nodeTask(TaskStage.STARTING);
        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(TaskConfig.builder().taskId(TASK_ID).taskWorkStage(TaskStage.STARTING).build())
            .build();

        when(nodeTaskRepository.findByTaskIdAndNodeId(TASK_ID, NODE_ID)).thenReturn(Mono.just(nodeTask));
        when(nodeTaskRepository.save(any(NodeTask.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(nodeTaskRepository.findAllByTaskId(TASK_ID)).thenReturn(Flux.just(nodeTask));
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));
        when(taskInfoMetadataRepository.updateStageById(eq(TASK_ID), eq(TaskStage.ONGOING.name()), any(Instant.class)))
            .thenReturn(Mono.empty());

        invokeHandleStateChange(TaskStateChangeEvent.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .fromStage(TaskStage.STARTING)
            .toStage(TaskStage.ONGOING)
            .triggerEvent(TaskEvent.ONGOING)
            .timestamp(Instant.parse("2026-06-02T08:43:50Z"))
            .eventSeq(1)
            .build());

        verify(taskMetricsSnapshotService, never()).collectAndSaveNodeSnapshot(
            anyString(), any(), anyString(), anyString(), anyString(), any(), any());
    }

    private static NodeTask nodeTask(TaskStage stage) {
        return NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .nodeName("node-name")
            .taskConfig(TaskConfig.builder()
                .taskId(TASK_ID)
                .nodeId(NODE_ID)
                .taskWorkStage(stage)
                .build())
            .currentStage(stage)
            .build();
    }

    private static TaskStateHistory history(Instant timestamp) {
        return TaskStateHistory.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .timestamp(timestamp)
            .build();
    }

    private void invokeHandleStateChange(TaskStateChangeEvent event) throws Exception {
        Method method = TaskStateEventHandler.class.getDeclaredMethod("handleStateChange", TaskStateChangeEvent.class);
        method.setAccessible(true);
        method.invoke(handler, event);
    }
}
