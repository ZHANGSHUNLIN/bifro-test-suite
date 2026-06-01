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

package org.apache.bifromq.testsuite.app.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.apache.bifromq.testsuite.app.bean.dto.BrokerEntry;
import org.apache.bifromq.testsuite.app.bean.dto.TaskRequest;
import org.apache.bifromq.testsuite.app.cluster.core.ClusterDataManager;
import org.apache.bifromq.testsuite.app.task.TaskManager;
import org.apache.bifromq.testsuite.app.database.pojo.MqttBroker;
import org.apache.bifromq.testsuite.app.database.pojo.MqttGroup;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.repository.MqttBrokerRepository;
import org.apache.bifromq.testsuite.app.database.repository.MqttGroupRepository;
import org.apache.bifromq.testsuite.app.database.repository.NodeTaskRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskMetricsSnapshotRepository;
import org.apache.bifromq.testsuite.app.database.service.TaskInfoMetadataService;
import org.apache.bifromq.testsuite.web.ApiException;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.TaskConfig.TaskType;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class TaskLifecycleTest {

    private static final String BROKER_ID = "broker-001";
    private static final String GROUP_ID = "group-001";
    private static final String TASK_NAME = "test-task-lc";
    @Mock
    private TaskInfoMetadataRepository taskInfoMetadataRepository;
    @Mock
    private NodeTaskRepository nodeTaskRepository;
    @Mock
    private MqttBrokerRepository mqttBrokerRepository;
    @Mock
    private TaskMetricsSnapshotRepository taskMetricsSnapshotRepository;
    @Mock
    private TaskInfoMetadataService taskInfoMetadataService;
    @Mock
    private ClusterDataManager clusterDataManager;
    @Mock
    private MqttGroupRepository groupRepository;
    @Mock
    private Vertx vertx;
    @Mock
    private EventBus eventBus;
    @InjectMocks
    private TaskManager taskManager;

    @BeforeEach
    void setUp() {
        lenient().when(vertx.eventBus()).thenReturn(eventBus);
        lenient().when(clusterDataManager.getCurrentNodeIdCache()).thenReturn("node-001");
        lenient().when(groupRepository.findById(GROUP_ID)).thenReturn(Mono.just(MqttGroup.builder()
            .id(GROUP_ID)
            .type("BROKER")
            .name("test-group")
            .build()));
    }

    

    
    @Test
    void addTask_validSpec_initialStateIsInit() {
        
        TaskRequest request = createValidTaskRequest(TASK_NAME);
        MqttBroker broker = createMqttBroker(BROKER_ID, GROUP_ID);

        TaskInfoMetadata savedMetadata = TaskInfoMetadata.builder()
            .taskId("task-lc-01")
            .taskName(TASK_NAME)
            .taskConfig(TaskConfig.builder()
                .taskId("task-lc-01")
                .taskWorkStage(TaskStage.INIT)
                .taskType(TaskType.PUBSUB)
                .template(TaskTemplate.PUBSUB_STANDARD)
                .build())
            .build();

        when(taskInfoMetadataRepository.findByTaskName(TASK_NAME)).thenReturn(Mono.empty());
        when(mqttBrokerRepository.findAllById(anyIterable())).thenReturn(Flux.just(broker));
        lenient().when(mqttBrokerRepository.findByBrokerId(BROKER_ID)).thenReturn(Mono.just(broker));
        lenient().when(mqttBrokerRepository.findByGroup(GROUP_ID)).thenReturn(Flux.just(broker));
        when(taskInfoMetadataService.insertTaskInfoMetadata(any(TaskInfoMetadata.class)))
            .thenReturn(Mono.just(savedMetadata));
        lenient().when(taskInfoMetadataRepository.save(any(TaskInfoMetadata.class)))
            .thenReturn(Mono.just(savedMetadata));

        
        TaskInfoMetadata result = taskManager.addTask(request).block();

        
        assertThat(result).isNotNull();
        assertThat(result.getTaskId())
            .as("SPEC-LC-01: taskId should not be empty")
            .isNotNull().isNotBlank();
        assertThat(result.getTaskConfig().getTaskWorkStage())
            .as("SPEC-LC-01: initial state must be INIT")
            .isEqualTo(TaskStage.INIT);
    }

    

    
    @Test
    void addTask_emptyBrokers_throwsIllegalArgument() {
        
        TaskRequest request = new TaskRequest();
        request.setTaskName(TASK_NAME + "-empty-brokers");
        request.setTaskType(TaskType.PUBSUB);
        request.setGroup(GROUP_ID);
        request.setTotalClientCount(10);
        request.setBrokers(new ArrayList<>());   

        
        assertThatThrownBy(() -> taskManager.addTask(request).block())
            .as("SPEC-LC-02: should throw exception when brokers is empty")
            .isInstanceOfAny(ApiException.class, IllegalArgumentException.class);
    }

    

    private TaskRequest createValidTaskRequest(String taskName) {
        BrokerEntry brokerEntry = new BrokerEntry();
        brokerEntry.setBrokerId(BROKER_ID);
        brokerEntry.setPort(1883);

        TaskRequest request = new TaskRequest();
        request.setTaskName(taskName);
        request.setTaskType(TaskType.PUBSUB);
        request.setGroup(GROUP_ID);
        request.setTotalClientCount(10);
        request.setBrokers(List.of(brokerEntry));
        return request;
    }

    private MqttBroker createMqttBroker(String brokerId, String groupId) {
        return MqttBroker.builder()
            .brokerId(brokerId)
            .name("test-broker")
            .host("localhost")
            .port(1883)
            .group(groupId)
            .build();
    }
}
