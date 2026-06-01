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

package org.apache.bifromq.testsuite.worker.type;

import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskSpec;

public record PubSubClientCountSpec(
    TaskTemplate template,
    int totalClientCount,
    int fanIn,
    int fanOut,
    int nodePubCount,
    int nodeSubCount
) {
    public static PubSubClientCountSpec fromTaskConfig(TaskConfig config) {
        return new PubSubClientCountSpec(
            config.getTemplate(),
            config.getTotalClientCount(),
            config.getFanIn(),
            config.getFanOut(),
            config.getNodePubCount(),
            config.getNodeSubCount()
        );
    }

    public static PubSubClientCountSpec fromWorkerTaskSpec(WorkerTaskSpec spec) {
        return new PubSubClientCountSpec(
            spec.getTemplate(),
            spec.getTotalClientCount(),
            spec.getFanIn(),
            spec.getFanOut(),
            spec.getNodePubCount(),
            spec.getNodeSubCount()
        );
    }
}
