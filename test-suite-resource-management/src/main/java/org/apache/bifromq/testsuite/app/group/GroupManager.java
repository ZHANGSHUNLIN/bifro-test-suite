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

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import org.apache.bifromq.testsuite.i18n.Messages;

import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.web.PageInfo;
import org.apache.bifromq.testsuite.app.bean.group.GroupListItem;
import org.apache.bifromq.testsuite.app.bean.group.GroupRequest;
import org.apache.bifromq.testsuite.app.database.pojo.MqttBroker;
import org.apache.bifromq.testsuite.app.database.pojo.MqttGroup;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.repository.MqttBrokerRepository;
import org.apache.bifromq.testsuite.app.database.repository.MqttGroupRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.web.ApiException;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@ConditionalOnControlPlane
public class GroupManager {

    public static final String TYPE_BROKER = "BROKER";
    public static final String TYPE_TASK = "TASK";
    public static final String TYPE_PROFILE = "PROFILE";

    @Resource
    private MqttGroupRepository groupRepository;

    @Resource
    private MqttBrokerRepository brokerRepository;

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;
    @Resource
    private org.apache.bifromq.testsuite.app.database.repository.WaveformProfileRepository waveformProfileRepository;

    
    public Mono<ApiResponse<MqttGroup>> add(@Valid GroupRequest request, String type) {
        return groupRepository.findByNameAndType(request.getName(), type)
            .flatMap(existing -> Mono.<ApiResponse<MqttGroup>>error(
                new ApiException(Messages.get("error.group.nameExists", request.getName()))))
            .switchIfEmpty(
                groupRepository.save(MqttGroup.builder()
                        .type(type)
                        .name(request.getName())
                        .description(request.getDescription())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build())
                    .map(ApiResponse::success)
            );
    }

    
    public Mono<ApiResponse<MqttGroup>> update(String id, @Valid GroupRequest request, String type) {
        return groupRepository.findById(id)
            .switchIfEmpty(Mono.error(new ApiException(Messages.get("error.group.notFound"))))
            .flatMap(existing -> {
                
                return groupRepository.findByNameAndType(request.getName(), type)
                    .filter(g -> !g.getId().equals(id))
                    .hasElement()
                    .flatMap(nameExists -> {
                        if (nameExists) {
                            return Mono.error(new ApiException(Messages.get("error.group.nameExists", request.getName())));
                        }
                        
                        BeanUtils.copyProperties(request, existing);
                        existing.setUpdatedAt(Instant.now());
                        return groupRepository.save(existing).map(ApiResponse::success);
                    });
            });
    }

    
    public Mono<ApiResponse<Void>> delete(String groupId, String type) {
        return groupRepository.findById(groupId)
            .switchIfEmpty(Mono.error(new ApiException(Messages.get("error.group.notFound"))))
            .flatMap(group -> {
                
                if (type != null && !type.equals(group.getType())) {
                    return Mono.error(new ApiException(Messages.get("error.group.typeMismatch")));
                }

                String actualType = group.getType();
                if (TYPE_BROKER.equals(actualType)) {
                    
                    return brokerRepository.findByGroup(group.getId())
                        .collectList()
                        .flatMap(brokers -> {
                            if (brokers.isEmpty()) {
                                
                                return groupRepository.deleteById(groupId)
                                    .then(Mono.just(ApiResponse.<Void>success()));
                            }
                            
                            String brokerNames = brokers.stream()
                                .map(MqttBroker::getName)
                                .collect(Collectors.joining(", "));
                            return Mono.error(new ApiException(Messages.get("error.group.usedByBrokers", brokerNames)));
                        });
                } else if (TYPE_TASK.equals(actualType)) {
                    
                    return taskInfoMetadataRepository.findByGroup(group.getId())
                        .collectList()
                        .flatMap(tasks -> {
                            if (tasks.isEmpty()) {
                                
                                return groupRepository.deleteById(groupId)
                                    .then(Mono.just(ApiResponse.<Void>success()));
                            }
                            
                            String taskNames = tasks.stream()
                                .map(TaskInfoMetadata::getTaskName)
                                .collect(Collectors.joining(", "));
                            return Mono.error(new ApiException(Messages.get("error.group.usedByTasks", taskNames)));
                        });
                } else if (TYPE_PROFILE.equals(actualType)) {
                    return waveformProfileRepository.findByGroup(group.getId())
                        .collectList()
                        .flatMap(profiles -> {
                            if (profiles.isEmpty()) {
                                return groupRepository.deleteById(groupId)
                                    .then(Mono.just(ApiResponse.<Void>success()));
                            }
                            String profileNames = profiles.stream()
                                .map(org.apache.bifromq.testsuite.app.database.pojo.WaveformProfile::getName)
                                .collect(Collectors.joining(", "));
                            return Mono.error(new ApiException(Messages.get("error.group.usedByProfiles", profileNames)));
                        });
                } else {
                    return groupRepository.deleteById(groupId)
                        .then(Mono.just(ApiResponse.<Void>success()));
                }
            });
    }

    
    public Mono<ApiResponse<PageInfo<GroupListItem>>> list(Integer pageNum, Integer pageSize, String type,
                                                           String name) {
        Flux<MqttGroup> baseFlux = groupRepository.findByType(type);

        
        Flux<MqttGroup> filteredFlux = (name != null && !name.trim().isEmpty())
            ? baseFlux.filter(group -> group.getName().contains(name.trim()))
            : baseFlux;

        return filteredFlux
            .collectList()
            .flatMapMany(groups -> Flux.fromIterable(groups)
                .flatMap(group -> {
                    GroupListItem item = new GroupListItem();
                    BeanUtils.copyProperties(group, item);
                    item.setType(type);

                    if (TYPE_BROKER.equals(type)) {
                        
                        return brokerRepository.findByGroup(group.getId())
                            .count()
                            .map(count -> {
                                item.setCount(count);
                                return item;
                            });
                    } else if (TYPE_TASK.equals(type)) {
                        
                        return taskInfoMetadataRepository.findByGroup(group.getId())
                            .count()
                            .map(count -> {
                                item.setCount(count);
                                return item;
                            });
                    } else if (TYPE_PROFILE.equals(type)) {
                        return waveformProfileRepository.findByGroup(group.getId())
                            .count()
                            .map(count -> {
                                item.setCount(count);
                                return item;
                            });
                    } else {
                        return Mono.just(item);
                    }
                }))
            .collectList()
            .map(content -> {
                
                int total = content.size();
                int fromIndex = (pageNum - 1) * pageSize;
                int toIndex = Math.min(fromIndex + pageSize, total);
                List<GroupListItem> pageList = total > fromIndex
                    ? content.subList(fromIndex, toIndex)
                    : new ArrayList<>();

                return ApiResponse.pageSuccess(pageList, (long) total, pageNum, pageSize);
            });
    }

    
    public Mono<ApiResponse<MqttGroup>> getDetail(String groupId) {
        return groupRepository.findById(groupId)
            .map(ApiResponse::success)
            .defaultIfEmpty(ApiResponse.error(Messages.get("error.group.notFound")));
    }

    
    public Flux<MqttGroup> getAllGroups(String type) {
        return groupRepository.findByType(type);
    }
}
