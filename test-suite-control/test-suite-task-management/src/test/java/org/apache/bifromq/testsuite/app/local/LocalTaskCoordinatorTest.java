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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.bifromq.testsuite.TaskSchedule;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.app.cluster.core.ClusterDataManager;
import org.apache.bifromq.testsuite.app.config.LocalPortModeProperties;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;
import org.apache.bifromq.testsuite.worker.TaskBroker;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.TaskWorker;
import org.apache.bifromq.testsuite.worker.TaskWorkerRuntime;
import org.apache.bifromq.testsuite.worker.WorkerTaskCommand;
import org.apache.bifromq.testsuite.worker.command.WorkerCommand;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandAck;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandAckStatus;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandType;
import org.apache.bifromq.testsuite.worker.pojo.TaskStopContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalTaskCoordinatorTest {

    private static final String TASK_ID = "test-task-123";
    private static final String NODE_ID = "node-abc123";
    private static final List<TaskBroker> TEST_BROKERS = List.of(
        TaskBroker.builder().host("localhost").port(1883).build()
    );

    @Mock
    private ClusterDataManager clusterDataManager;
    @Mock
    private Vertx vertx;
    @Mock
    private EventBus eventBus;
    @Mock
    private LocalPortModeProperties localPortModeProperties;
    @Mock
    private TaskWorkerRuntime taskWorkerRuntime;
    @Mock
    private TaskWorker taskWorker;
    @InjectMocks
    private LocalTaskCoordinator localTaskCoordinator;

    @BeforeEach
    void setUp() {
        lenient().when(clusterDataManager.getCurrentNodeIdCache()).thenReturn(NODE_ID);
        lenient().when(vertx.eventBus()).thenReturn(eventBus);
        lenient().when(localPortModeProperties.toConfig()).thenReturn(new LocalPortRangeConfig());
        lenient().when(taskWorkerRuntime.create(any(), any())).thenReturn(taskWorker);
        lenient().when(taskWorkerRuntime.runningTaskStages(any())).thenAnswer(invocation -> {
            Map<String, TaskWorker> runningTasks = invocation.getArgument(0);
            return runningTasks.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getTaskState()));
        });
        lenient().when(taskWorker.terminalFuture()).thenReturn(new java.util.concurrent.CompletableFuture<>());
        lenient().when(taskWorker.getTaskState()).thenReturn(TaskStage.ONGOING);
        lenient().when(vertx.executeBlocking(any(Callable.class)))
            .thenAnswer(invocation -> {
                Callable<?> callable = invocation.getArgument(0);
                try {
                    return Future.succeededFuture(callable.call());
                } catch (Exception e) {
                    return Future.failedFuture(e);
                }
            });
    }

    @Test
    void startTaskById_shouldIgnoreLegacyDbBackedStart() {
        localTaskCoordinator.startTask(TASK_ID);

        assertEquals(0, localTaskCoordinator.runningTask().size());
    }

    @Test
    void handleWorkerCommand_startShouldRunOnWorkerExecutor() {
        localTaskCoordinator.handleWorkerCommand(WorkerTaskCommand.fromTaskConfig(taskConfig()));

        verify(vertx, atLeastOnce()).executeBlocking(any(Callable.class));
    }

    @Test
    void handleWorkerCommandEnvelope_startShouldAckAcceptedAndRunOnWorkerExecutor() {
        WorkerCommand command = WorkerCommand.builder()
            .messageId("message-1")
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .type(WorkerCommandType.START_TASK)
            .startCommand(WorkerTaskCommand.fromTaskConfig(taskConfig()))
            .build();

        WorkerCommandAck ack = localTaskCoordinator.handleWorkerCommand(command);

        assertEquals(WorkerCommandAckStatus.ACCEPTED, ack.getStatus());
        verify(vertx, atLeastOnce()).executeBlocking(any(Callable.class));
    }

    @Test
    void handleWorkerCommandEnvelope_startShouldRejectWhenShuttingDown() {
        localTaskCoordinator.markShuttingDown();
        WorkerCommand command = WorkerCommand.builder()
            .messageId("message-shutdown")
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .type(WorkerCommandType.START_TASK)
            .startCommand(WorkerTaskCommand.fromTaskConfig(taskConfig()))
            .build();

        WorkerCommandAck ack = localTaskCoordinator.handleWorkerCommand(command);

        assertEquals(WorkerCommandAckStatus.REJECTED_INVALID_STATE, ack.getStatus());
    }

    @Test
    void handleWorkerCommandEnvelope_duplicateShouldAckIgnored() {
        WorkerCommand command = WorkerCommand.builder()
            .messageId("message-dup")
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .type(WorkerCommandType.STOP_TASK)
            .build();

        WorkerCommandAck firstAck = localTaskCoordinator.handleWorkerCommand(command);
        WorkerCommandAck secondAck = localTaskCoordinator.handleWorkerCommand(command);

        assertEquals(WorkerCommandAckStatus.ACCEPTED, firstAck.getStatus());
        assertEquals(WorkerCommandAckStatus.IGNORED_DUPLICATE, secondAck.getStatus());
    }

    @Test
    void handleWorkerCommandEnvelope_duplicateStartShouldAckIgnored() {
        WorkerCommand command = WorkerCommand.builder()
            .messageId("message-start-dup")
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .type(WorkerCommandType.START_TASK)
            .startCommand(WorkerTaskCommand.fromTaskConfig(taskConfig()))
            .build();

        WorkerCommandAck firstAck = localTaskCoordinator.handleWorkerCommand(command);
        WorkerCommandAck secondAck = localTaskCoordinator.handleWorkerCommand(command);

        assertEquals(WorkerCommandAckStatus.ACCEPTED, firstAck.getStatus());
        assertEquals(WorkerCommandAckStatus.IGNORED_DUPLICATE, secondAck.getStatus());
    }

    @Test
    void handleWorkerCommandEnvelope_givenMismatchedNode_shouldReject() {
        WorkerCommand command = WorkerCommand.builder()
            .messageId("message-node")
            .taskId(TASK_ID)
            .nodeId("other-node")
            .type(WorkerCommandType.STOP_TASK)
            .build();

        WorkerCommandAck ack = localTaskCoordinator.handleWorkerCommand(command);

        assertEquals(WorkerCommandAckStatus.REJECTED_INVALID_PAYLOAD, ack.getStatus());
    }

    @Test
    void handleLegacyStartCommand_shouldNotStartWorker() {
        TaskSchedule command = TaskSchedule.builder()
            .op(TaskSchedule.Op.REG)
            .id(TASK_ID)
            .build();

        localTaskCoordinator.handleTaskCommand(command);

        assertEquals(0, localTaskCoordinator.runningTask().size());
    }

    @Test
    void startTask_givenPreflightExcludedPorts_shouldMergeLocalPortConfig() {
        LocalPortRangeConfig globalConfig = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(65535)
            .excludedPorts(List.of(20000))
            .build();
        LocalPortRangeConfig preflightConfig = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(65535)
            .excludedPorts(List.of(25249, 53020))
            .build();
        lenient().when(localPortModeProperties.toConfig()).thenReturn(globalConfig);

        TaskConfig taskConfig = taskConfig();
        taskConfig.setLocalPortRangeConfig(preflightConfig);
        WorkerTaskCommand command = WorkerTaskCommand.fromTaskConfig(taskConfig);

        localTaskCoordinator.startTask(command);

        LocalPortRangeConfig workerConfig = command.workerTaskSpec().getLocalPortRangeConfig();
        assertEquals(List.of(20000, 25249, 53020), workerConfig.getExcludedPorts());
    }

    @Test
    void runningTask_shouldReturnEmptyMapWhenNoTasksRunning() {
        Map<String, TaskStage> result = localTaskCoordinator.runningTask();

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void stopTask_whenNoLocalWorker_shouldHandleGracefully() {
        localTaskCoordinator.stopTask(TASK_ID);

        assertEquals(0, localTaskCoordinator.runningTask().size());
    }

    @Test
    void stopTask_givenServiceShutdownContextAndNoLocalWorker_shouldComplete() throws Exception {
        localTaskCoordinator.stopTask(TASK_ID, TaskStopContext.serviceShutdown(NODE_ID)).get();

        assertEquals(0, localTaskCoordinator.runningTask().size());
    }

    private TaskConfig taskConfig() {
        return TaskConfig.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(50)
            .brokers(TEST_BROKERS)
            .build();
    }
}
