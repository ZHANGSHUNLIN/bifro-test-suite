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

package org.apache.bifromq.testsuite.security.application;

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.bifromq.testsuite.security.domain.SystemUser;
import org.apache.bifromq.testsuite.security.infrastructure.SystemUserRepository;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@ConditionalOnControlPlane
public class SystemUserDetailsService implements ReactiveUserDetailsService {

    private final SystemUserRepository systemUserRepository;

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return systemUserRepository.findByUsername(username)
            .filter(SystemUser::isEnabled)
            .map(user -> User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .authorities(user.getRoles().stream()
                    .map(role -> role.toUpperCase(Locale.ROOT))
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .toArray(String[]::new))
                .build());
    }
}
