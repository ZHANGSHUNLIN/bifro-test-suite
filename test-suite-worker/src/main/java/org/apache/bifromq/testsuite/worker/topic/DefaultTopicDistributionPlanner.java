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

import java.util.List;
import java.util.stream.IntStream;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.models.TopicFilter;
import org.apache.bifromq.testsuite.worker.context.TaskExecutionConfig;

public class DefaultTopicDistributionPlanner implements TopicDistributionPlanner {
    private final String taskId;
    private final TaskExecutionConfig config;
    private final int thingIdStartAt;
    private final int fanOut;
    private final int fanIn;

    public DefaultTopicDistributionPlanner(String taskId, TaskExecutionConfig config, int thingIdStartAt,
                                           int fanOut, int fanIn) {
        this.taskId = taskId;
        this.config = config;
        this.thingIdStartAt = thingIdStartAt;
        this.fanOut = fanOut;
        this.fanIn = fanIn;
    }

    @Override
    public TopicAssignment publisherAssignment(int clientIndex) {
        int globalIndex = thingIdStartAt + clientIndex;
        int topicIndex = fanIn > 1 ? globalIndex / fanIn : globalIndex;
        return TopicAssignment.forPublisher(clientTopics(topicIndex, false));
    }

    @Override
    public TopicAssignment subscriberAssignment(int clientIndex) {
        int globalIndex = thingIdStartAt + clientIndex;
        int topicIndex = fanOut > 1 ? globalIndex / fanOut : globalIndex;
        var filters = clientTopics(topicIndex, true).stream()
            .map(topic -> new TopicFilter(topic, config.qos()))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return TopicAssignment.forSubscriber(filters);
    }

    private List<String> clientTopics(int topicIndex, boolean subscriber) {
        int topicCount = Math.max(1, config.topicsPerClient());
        if (topicCount == 1) {
            return List.of(clientTopic(topicIndex, subscriber));
        }
        return IntStream.range(0, topicCount)
            .mapToObj(offset -> clientTopicWithOffset(topicIndex, offset, subscriber))
            .toList();
    }

    private String clientTopic(int topicIndex, boolean subscriber) {
        String baseTopic;
        if (usesConfiguredTopic()) {
            baseTopic = config.topic();
        } else {
            baseTopic = String.format("%s/%d", taskId, topicIndex);
        }
        return withWildcardSuffix(baseTopic, subscriber);
    }

    private String clientTopicWithOffset(int topicIndex, int topicOffset, boolean subscriber) {
        String baseTopic;
        if (usesConfiguredTopic()) {
            baseTopic = String.format("%s/%d", config.topic(), topicOffset);
        } else {
            baseTopic = String.format("%s/%d/%d", taskId, topicIndex, topicOffset);
        }
        return withWildcardSuffix(baseTopic, subscriber);
    }

    private boolean usesConfiguredTopic() {
        return (config.template() == TaskTemplate.PUBSUB_SUB_ONLY || config.template() == TaskTemplate.PUBSUB_PUB_ONLY)
            && config.topic() != null
            && !config.topic().isEmpty();
    }

    private String withWildcardSuffix(String baseTopic, boolean subscriber) {
        if (!config.wildcard()) {
            return baseTopic;
        }
        return baseTopic + "/" + (subscriber ? "+" : "suffix");
    }
}
