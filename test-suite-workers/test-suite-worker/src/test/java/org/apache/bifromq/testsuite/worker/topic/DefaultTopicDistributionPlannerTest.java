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

package org.apache.bifromq.testsuite.worker.topic;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.List;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.models.TopicFilter;
import org.apache.bifromq.testsuite.worker.context.TaskExecutionConfig;
import org.junit.jupiter.api.Test;

class DefaultTopicDistributionPlannerTest {

    @Test
    void publisherAssignment_withConfiguredTopicTemplate_rendersTopicContext() {
        DefaultTopicDistributionPlanner planner = new DefaultTopicDistributionPlanner(
            "taskA",
            config("device/{{task_id}}/{{node_id}}/{{index}}/{{topic_offset}}", 2, false),
            10, 1, 1);

        TopicAssignment assignment = planner.publisherAssignment(3);

        assertThat(assignment.publishTopics())
            .containsExactly("device/taskA/node-1/13/0", "device/taskA/node-1/13/1");
    }

    @Test
    void subscriberAssignment_withWildcardTopicTemplate_appliesWildcardAfterRender() {
        DefaultTopicDistributionPlanner planner = new DefaultTopicDistributionPlanner(
            "taskA",
            config("device/{{topic_index}}/{{topic_offset}}", 2, true),
            0, 1, 1);

        TopicAssignment assignment = planner.subscriberAssignment(4);

        assertThat(assignment.subscribeFilters())
            .extracting(TopicFilter::getName)
            .containsExactlyInAnyOrder("device/4/0/+", "device/4/1/+");
    }

    private TaskExecutionConfig config(String topic, int topicsPerClient, boolean wildcard) {
        return new TaskExecutionConfig(
            "taskA",
            "node-1",
            "PUBSUB",
            TaskTemplate.PUBSUB_PUB_ONLY,
            List.of(),
            32,
            1,
            false,
            false,
            MqttQoS.AT_MOST_ONCE,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            topic,
            topicsPerClient,
            wildcard,
            1,
            null,
            null,
            null,
            null,
            null,
            null);
    }
}
