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
import java.util.Set;
import org.apache.bifromq.testsuite.models.TopicFilter;

public record TopicAssignment(
    String primaryTopic,
    List<String> publishTopics,
    Set<TopicFilter> subscribeFilters
) {
    public static TopicAssignment forPublisher(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            throw new IllegalArgumentException("topics must not be empty");
        }
        return new TopicAssignment(topics.get(0), List.copyOf(topics), Set.of());
    }

    public static TopicAssignment forSubscriber(Set<TopicFilter> filters) {
        return new TopicAssignment(null, List.of(), Set.copyOf(filters));
    }
}
