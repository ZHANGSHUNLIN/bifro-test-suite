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

import org.apache.bifromq.testsuite.app.database.pojo.MqttGroup;
import org.apache.bifromq.testsuite.app.database.repository.MqttGroupRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnControlPlane
public final class DefaultGroupInitializer implements ApplicationRunner {

    public static final String DEFAULT_GROUP_NAME = "Default Group";
    public static final String DEFAULT_GROUP_DESCRIPTION = "System default group";

    private static final List<String> DEFAULT_GROUP_TYPES = List.of(
        GroupManager.TYPE_BROKER,
        GroupManager.TYPE_TASK,
        GroupManager.TYPE_PROFILE);

    private final MqttGroupRepository groupRepository;

    @Override
    public void run(ApplicationArguments args) {
        DEFAULT_GROUP_TYPES.forEach(this::ensureDefaultGroup);
    }

    private void ensureDefaultGroup(String type) {
        groupRepository.findByNameAndType(DEFAULT_GROUP_NAME, type)
            .switchIfEmpty(groupRepository.save(MqttGroup.builder()
                .type(type)
                .name(DEFAULT_GROUP_NAME)
                .description(DEFAULT_GROUP_DESCRIPTION)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build()))
            .doOnError(e -> log.warn("Failed to initialize default {} group", type, e))
            .block();
    }
}
