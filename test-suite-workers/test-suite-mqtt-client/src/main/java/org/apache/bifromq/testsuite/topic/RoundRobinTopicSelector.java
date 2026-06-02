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

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class RoundRobinTopicSelector implements TopicSelector {
    private final List<String> topics;
    private final AtomicLong sequence = new AtomicLong();

    public RoundRobinTopicSelector(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            throw new IllegalArgumentException("topics must not be empty");
        }
        this.topics = List.copyOf(topics);
    }

    @Override
    public String nextTopic() {
        long next = sequence.getAndIncrement();
        int topicIndex = (int) Math.floorMod(next, topics.size());
        return topics.get(topicIndex);
    }
}
