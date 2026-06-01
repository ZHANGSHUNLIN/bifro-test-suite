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

public final class PubSubClientCountPlanner {

    private PubSubClientCountPlanner() {
    }

    public static int expectedPubCount(PubSubClientCountSpec spec) {
        if (spec.nodePubCount() >= 0) {
            return spec.nodePubCount();
        }
        if (spec.template() == TaskTemplate.PUBSUB_PUB_ONLY) {
            return spec.totalClientCount();
        } else if (spec.template() == TaskTemplate.PUBSUB_SUB_ONLY) {
            return 0;
        } else if (spec.fanIn() > 1 && spec.fanOut() > 1) {
            return spec.fanIn();
        } else if (spec.fanIn() > 1) {
            return spec.fanIn() * (spec.totalClientCount() / (spec.fanIn() + 1));
        } else {
            return spec.totalClientCount() / (spec.fanOut() + 1);
        }
    }

    public static int expectedSubCount(PubSubClientCountSpec spec) {
        if (spec.nodeSubCount() >= 0) {
            return spec.nodeSubCount();
        }
        if (spec.template() == TaskTemplate.PUBSUB_SUB_ONLY) {
            return spec.totalClientCount();
        } else if (spec.template() == TaskTemplate.PUBSUB_PUB_ONLY) {
            return 0;
        } else if (spec.fanIn() > 1 && spec.fanOut() > 1) {
            return spec.fanOut();
        } else if (spec.fanIn() > 1) {
            return spec.totalClientCount() / (spec.fanIn() + 1);
        } else {
            return spec.fanOut() * (spec.totalClientCount() / (spec.fanOut() + 1));
        }
    }
}
