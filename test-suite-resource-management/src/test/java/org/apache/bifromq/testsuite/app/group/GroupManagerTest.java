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

package org.apache.bifromq.testsuite.app.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.web.PageInfo;
import org.apache.bifromq.testsuite.app.bean.group.GroupListItem;
import org.apache.bifromq.testsuite.app.database.pojo.MqttGroup;
import org.apache.bifromq.testsuite.app.database.pojo.MqttBroker;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.repository.MqttBrokerRepository;
import org.apache.bifromq.testsuite.app.database.repository.MqttGroupRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.web.ApiException;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class GroupManagerTest {

    @Mock
    private MqttGroupRepository groupRepository;

    @Mock
    private MqttBrokerRepository brokerRepository;

    @Mock
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @InjectMocks
    private GroupManager groupManager;

    @Test
    void list_taskGroupCountsTasksByGroupId() {
        MqttGroup taskGroup = MqttGroup.builder()
            .id("group-id-1")
            .type(GroupManager.TYPE_TASK)
            .name("Task Group A")
            .description("desc")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        TaskInfoMetadata task1 = TaskInfoMetadata.builder()
            .taskId("task-1")
            .taskName("task-1")
            .group("group-id-1")
            .createTime(LocalDateTime.now())
            .build();
        TaskInfoMetadata task2 = TaskInfoMetadata.builder()
            .taskId("task-2")
            .taskName("task-2")
            .group("group-id-1")
            .createTime(LocalDateTime.now())
            .build();

        when(groupRepository.findByType(GroupManager.TYPE_TASK)).thenReturn(Flux.just(taskGroup));
        when(taskInfoMetadataRepository.findByGroup("group-id-1")).thenReturn(Flux.just(task1, task2));

        ApiResponse<PageInfo<GroupListItem>> response = groupManager.list(1, 10, GroupManager.TYPE_TASK, null).block();

        assertThat(response).isNotNull();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getContent()).hasSize(1);
        GroupListItem item = response.getData().getContent().get(0);
        assertThat(item.getId()).isEqualTo("group-id-1");
        assertThat(item.getTaskCount()).isEqualTo(2L);

        verify(taskInfoMetadataRepository).findByGroup("group-id-1");
        verify(taskInfoMetadataRepository, never()).findByGroup("Task Group A");
    }

    @Test
    void delete_taskGroupChecksUsageByGroupId() {
        MqttGroup taskGroup = MqttGroup.builder()
            .id("group-id-1")
            .type(GroupManager.TYPE_TASK)
            .name("Task Group A")
            .description("desc")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        TaskInfoMetadata task = TaskInfoMetadata.builder()
            .taskId("task-1")
            .taskName("task-1")
            .group("group-id-1")
            .createTime(LocalDateTime.now())
            .build();

        when(groupRepository.findById("group-id-1")).thenReturn(Mono.just(taskGroup));
        when(taskInfoMetadataRepository.findByGroup("group-id-1")).thenReturn(Flux.just(task));

        ApiException exception = assertThrows(ApiException.class,
            () -> groupManager.delete("group-id-1", GroupManager.TYPE_TASK).block());
        assertThat(exception.getMessage()).isEqualTo("error.group.usedByTasks");

        verify(taskInfoMetadataRepository).findByGroup("group-id-1");
        verify(groupRepository, never()).deleteById("group-id-1");
    }

    @Test
    void delete_unusedHistoricalDefaultTaskGroupSucceeds() {
        MqttGroup taskGroup = MqttGroup.builder()
            .id("default-task-group")
            .type(GroupManager.TYPE_TASK)
            .name(DefaultGroupInitializer.DEFAULT_GROUP_NAME)
            .description("desc")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(groupRepository.findById("default-task-group")).thenReturn(Mono.just(taskGroup));
        when(taskInfoMetadataRepository.findByGroup("default-task-group")).thenReturn(Flux.empty());
        when(groupRepository.deleteById("default-task-group")).thenReturn(Mono.empty());

        ApiResponse<Void> response = groupManager.delete("default-task-group", GroupManager.TYPE_TASK).block();

        assertThat(response).isNotNull();
        verify(taskInfoMetadataRepository).findByGroup("default-task-group");
        verify(groupRepository).deleteById("default-task-group");
    }

    @Test
    void delete_usedHistoricalDefaultBrokerGroupStillBlockedByUsage() {
        MqttGroup brokerGroup = MqttGroup.builder()
            .id("default-broker-group")
            .type(GroupManager.TYPE_BROKER)
            .name(DefaultGroupInitializer.DEFAULT_GROUP_NAME)
            .description("desc")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        MqttBroker broker = MqttBroker.builder()
            .id("broker-id-1")
            .brokerId("broker-1")
            .name("broker-1")
            .group("default-broker-group")
            .build();

        when(groupRepository.findById("default-broker-group")).thenReturn(Mono.just(brokerGroup));
        when(brokerRepository.findByGroup("default-broker-group")).thenReturn(Flux.just(broker));

        ApiException exception = assertThrows(ApiException.class,
            () -> groupManager.delete("default-broker-group", GroupManager.TYPE_BROKER).block());
        assertThat(exception.getMessage()).isEqualTo("error.group.usedByBrokers");

        verify(brokerRepository).findByGroup("default-broker-group");
        verify(groupRepository, never()).deleteById("default-broker-group");
    }
}
