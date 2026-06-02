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

import org.apache.bifromq.testsuite.app.database.pojo.WaveformProfile;
import org.apache.bifromq.testsuite.app.database.repository.WaveformProfileRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

/**
 * Validator for {@link ProfileExists} annotation.
 * Validates that a profile with the given ID exists in the database.
 * Note: WaveformProfile does not have a type field, so type validation is skipped.
 */
@Component
@ConditionalOnControlPlane
public class ProfileExistsValidator implements ConstraintValidator<ProfileExists, String> {

    @Autowired
    private WaveformProfileRepository profileRepository;

    private String type;

    @Override
    public void initialize(ProfileExists constraintAnnotation) {
        this.type = constraintAnnotation.type();
    }

    @Override
    public boolean isValid(String profileId, ConstraintValidatorContext context) {
        if (profileId == null || profileId.isBlank()) {
            return true;
        }

        WaveformProfile profile = profileRepository.findById(profileId)
            .subscribeOn(Schedulers.boundedElastic())
            .block();
        if (profile == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Profile not found: " + profileId
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
