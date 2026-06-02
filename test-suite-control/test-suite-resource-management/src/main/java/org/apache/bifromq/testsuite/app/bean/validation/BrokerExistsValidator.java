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

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import org.apache.bifromq.testsuite.app.database.pojo.MqttBroker;
import org.apache.bifromq.testsuite.app.database.repository.MqttBrokerRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

/**
 * Validator for {@link BrokerExists} annotation.
 * Validates that a broker with the given ID exists in the database.
 */
@Component
@ConditionalOnControlPlane
public class BrokerExistsValidator implements ConstraintValidator<BrokerExists, String> {

    @Autowired
    private MqttBrokerRepository brokerRepository;

    @Override
    public boolean isValid(String brokerId, ConstraintValidatorContext context) {
        if (brokerId == null || brokerId.isBlank()) {
            return true;
        }

        MqttBroker broker = brokerRepository.findByBrokerId(brokerId)
            .subscribeOn(Schedulers.boundedElastic())
            .block();
        if (broker == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Broker not found: " + brokerId
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
