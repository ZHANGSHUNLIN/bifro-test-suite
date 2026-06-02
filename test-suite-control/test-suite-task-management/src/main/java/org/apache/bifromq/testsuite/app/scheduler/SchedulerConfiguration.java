/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.app.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;
import java.util.List;
import org.apache.bifromq.testsuite.config.role.ConditionalOnWorkerPlane;
import org.apache.bifromq.testsuite.scheduler.DelayedTaskScheduler;
import org.apache.bifromq.testsuite.scheduler.ScheduledTaskExecutor;
import org.apache.bifromq.testsuite.scheduler.ScheduledTaskExecutorRegistry;
import org.apache.bifromq.testsuite.scheduler.SchedulerProperties;
import org.apache.bifromq.testsuite.scheduler.VertxDelayedTaskScheduler;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnWorkerPlane
public class SchedulerConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "bifro.scheduler")
    public SchedulerProperties schedulerProperties() {
        return new SchedulerProperties();
    }

    @Bean
    public ScheduledTaskExecutor taskMetricsCleanupExecutor() {
        return new TaskMetricsCleanupExecutor();
    }

    @Bean
    public ScheduledTaskExecutorRegistry scheduledTaskExecutorRegistry(List<ScheduledTaskExecutor> executors) {
        return new ScheduledTaskExecutorRegistry(executors);
    }

    @Bean(destroyMethod = "close")
    public DelayedTaskScheduler delayedTaskScheduler(Vertx vertx,
                                                     ScheduledTaskExecutorRegistry executorRegistry,
                                                     SchedulerProperties schedulerProperties,
                                                     MeterRegistry meterRegistry) {
        return new VertxDelayedTaskScheduler(vertx, executorRegistry, schedulerProperties, meterRegistry);
    }
}
