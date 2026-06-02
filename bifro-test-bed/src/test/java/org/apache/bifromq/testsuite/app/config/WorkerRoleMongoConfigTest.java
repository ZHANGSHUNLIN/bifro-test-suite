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
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

class WorkerRoleMongoConfigTest {

    @Test
    void mongoConfig_givenWorkerRole_shouldNotRegisterMongoConversionBean() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("testRole", Map.of("bifro.node-role", "worker")));
        context.register(MongoConfig.class);
        context.refresh();

        try (context) {
            assertThat(context.getBeansOfType(MongoCustomConversions.class)).isEmpty();
        }
    }

    @Test
    void mongoConfig_givenControlRole_shouldRegisterMongoConversionBean() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("testRole", Map.of("bifro.node-role", "control")));
        context.register(MongoConfig.class);
        context.refresh();

        try (context) {
            assertThat(context.getBeansOfType(MongoCustomConversions.class)).hasSize(1);
        }
    }
}
