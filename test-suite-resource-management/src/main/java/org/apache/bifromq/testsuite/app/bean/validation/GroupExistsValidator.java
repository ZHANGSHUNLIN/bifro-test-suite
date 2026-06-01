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

package org.apache.bifromq.testsuite.app.bean.validation;

import org.apache.bifromq.testsuite.app.database.pojo.MqttGroup;
import org.apache.bifromq.testsuite.app.database.repository.MqttGroupRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

/**
 * Validator for {@link GroupExists} annotation.
 * Validates that a group with the given ID exists in the database.
 */
@Component
public class GroupExistsValidator implements ConstraintValidator<GroupExists, String> {

    @Autowired
    private MqttGroupRepository groupRepository;

    private String type;

    @Override
    public void initialize(GroupExists constraintAnnotation) {
        this.type = constraintAnnotation.type();
    }

    @Override
    public boolean isValid(String groupId, ConstraintValidatorContext context) {
        if (groupId == null || groupId.isBlank()) {
            return true;
        }

        MqttGroup group = groupRepository.findById(groupId)
            .subscribeOn(Schedulers.boundedElastic())
            .block();
        if (group == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Group not found: " + groupId
            ).addConstraintViolation();
            return false;
        }

        if (!type.isEmpty() && !type.equals(group.getType())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Group type mismatch: expected " + type + " but found " + group.getType()
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
