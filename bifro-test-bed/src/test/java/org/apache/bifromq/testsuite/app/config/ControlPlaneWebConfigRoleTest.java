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

package org.apache.bifromq.testsuite.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.bifromq.testsuite.app.controller.HealthApi;
import org.apache.bifromq.testsuite.app.exception.BifroExceptionHandler;
import org.apache.bifromq.testsuite.app.i18n.LocaleFilter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class ControlPlaneWebConfigRoleTest {

    @Test
    void webControlPlaneBeans_givenWorkerRole_shouldNotRegister() {
        try (AnnotationConfigApplicationContext context = contextWithRole("worker")) {
            assertThat(context.getBeansOfType(OpenApiConfig.class)).isEmpty();
            assertThat(context.getBeansOfType(CorsConfig.class)).isEmpty();
            assertThat(context.getBeansOfType(LoggingWebFilter.class)).isEmpty();
            assertThat(context.getBeansOfType(HealthApi.class)).isEmpty();
            assertThat(context.getBeansOfType(BifroExceptionHandler.class)).isEmpty();
            assertThat(context.getBeansOfType(LocaleFilter.class)).isEmpty();
        }
    }

    @Test
    void webControlPlaneBeans_givenControlRole_shouldRegisterConfigurationClasses() {
        try (AnnotationConfigApplicationContext context = contextWithRole("control")) {
            assertThat(context.getBeansOfType(OpenApiConfig.class)).hasSize(1);
            assertThat(context.getBeansOfType(CorsConfig.class)).hasSize(1);
            assertThat(context.getBeansOfType(LoggingWebFilter.class)).hasSize(1);
            assertThat(context.getBeansOfType(HealthApi.class)).hasSize(1);
            assertThat(context.getBeansOfType(BifroExceptionHandler.class)).hasSize(1);
            assertThat(context.getBeansOfType(LocaleFilter.class)).hasSize(1);
        }
    }

    private AnnotationConfigApplicationContext contextWithRole(String role) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("testRole", Map.of("bifro.node-role", role)));
        context.register(OpenApiConfig.class);
        context.register(CorsConfig.class);
        context.register(LoggingWebFilter.class);
        context.register(HealthApi.class);
        context.register(BifroExceptionHandler.class);
        context.register(LocaleFilter.class);
        context.refresh();
        return context;
    }
}
