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

package org.apache.bifromq.testsuite.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoundRobinTopicSelectorTest {
    @Test
    void nextTopic_cyclesThroughTopics() {
        TopicSelector selector = TopicSelectors.roundRobin(List.of("topic/0", "topic/1", "topic/2"));

        assertThat(selector.nextTopic()).isEqualTo("topic/0");
        assertThat(selector.nextTopic()).isEqualTo("topic/1");
        assertThat(selector.nextTopic()).isEqualTo("topic/2");
        assertThat(selector.nextTopic()).isEqualTo("topic/0");
    }

    @Test
    void constructor_rejectsEmptyTopics() {
        assertThatThrownBy(() -> TopicSelectors.roundRobin(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("topics must not be empty");
    }
}
