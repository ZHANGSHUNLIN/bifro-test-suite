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

package org.apache.bifromq.testsuite.config.role;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.bifromq.testsuite.cluster.NodeRole;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

class NodeRoleConditionTest {

    @Test
    void nodeRoleCondition_givenDefaultRole_shouldLoadControlAndWorkerBeans() {
        try (AnnotationConfigApplicationContext context = contextWithRole(null)) {
            assertThat(context.containsBean("controlBean")).isTrue();
            assertThat(context.containsBean("workerBean")).isTrue();
        }
    }

    @Test
    void nodeRoleCondition_givenControlRole_shouldLoadOnlyControlBean() {
        try (AnnotationConfigApplicationContext context = contextWithRole("control")) {
            assertThat(context.containsBean("controlBean")).isTrue();
            assertThat(context.containsBean("workerBean")).isFalse();
        }
    }

    @Test
    void nodeRoleCondition_givenWorkerRole_shouldLoadOnlyWorkerBean() {
        try (AnnotationConfigApplicationContext context = contextWithRole("worker")) {
            assertThat(context.containsBean("controlBean")).isFalse();
            assertThat(context.containsBean("workerBean")).isTrue();
        }
    }

    @Test
    void nodeRoleProperties_givenKebabCaseProperty_shouldResolveNodeRole() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("testRole", Map.of("bifro.node-role", "worker")));
        context.registerBean(NodeRoleProperties.class);
        context.refresh();

        try (context) {
            assertThat(context.getBean(NodeRoleProperties.class).getNodeRole()).isEqualTo(NodeRole.WORKER);
        }
    }

    private AnnotationConfigApplicationContext contextWithRole(String role) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (role != null) {
            context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("testRole", Map.of("bifro.nodeRole", role)));
        }
        context.register(TestConfig.class);
        context.refresh();
        return context;
    }

    @Configuration
    static class TestConfig {
        @Bean
        @ConditionalOnControlPlane
        String controlBean() {
            return "control";
        }

        @Bean
        @ConditionalOnWorkerPlane
        String workerBean() {
            return "worker";
        }
    }
}
