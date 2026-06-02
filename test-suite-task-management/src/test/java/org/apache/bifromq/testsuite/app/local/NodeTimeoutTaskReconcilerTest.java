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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.repository.NodeTaskRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class NodeTimeoutTaskReconcilerTest {

    private static final String TASK_ID = "task-1";
    private static final String NODE_ID = "node-timeout";

    @Mock
    private NodeTaskRepository nodeTaskRepository;
    @Mock
    private TaskInfoMetadataRepository taskInfoMetadataRepository;
    @InjectMocks
    private NodeTimeoutTaskReconciler reconciler;

    @Test
    void handleNodeTimeout_shouldMarkNodeTaskFailedAndUpdateMainTaskWhenAllTerminal() {
        NodeTask nodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .taskConfig(TaskConfig.builder()
                .taskId(TASK_ID)
                .nodeId(NODE_ID)
                .taskWorkStage(TaskStage.ONGOING)
                .build())
            .build();
        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(TaskConfig.builder()
                .taskId(TASK_ID)
                .taskWorkStage(TaskStage.ONGOING)
                .build())
            .build();

        when(nodeTaskRepository.findAllByNodeId(NODE_ID)).thenReturn(Flux.just(nodeTask));
        when(nodeTaskRepository.save(any(NodeTask.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(nodeTaskRepository.findAllByTaskId(TASK_ID)).thenReturn(Flux.just(nodeTask));
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));
        when(taskInfoMetadataRepository.updateStageById(eq(TASK_ID), eq(TaskStage.FAILED.name()),
            any(Instant.class))).thenReturn(Mono.empty());

        reconciler.handleNodeTimeout(NODE_ID);

        assertEquals(TaskStage.FAILED, nodeTask.getCurrentStage());
        verify(taskInfoMetadataRepository).updateStageById(eq(TASK_ID), eq(TaskStage.FAILED.name()),
            any(Instant.class));
    }

    @Test
    void handleNodeTimeout_duplicateNodeTimeoutShouldOnlyProcessOnce() {
        when(nodeTaskRepository.findAllByNodeId(NODE_ID)).thenReturn(Flux.empty());

        reconciler.handleNodeTimeout(NODE_ID);
        reconciler.handleNodeTimeout(NODE_ID);

        verify(nodeTaskRepository).findAllByNodeId(NODE_ID);
    }
}
