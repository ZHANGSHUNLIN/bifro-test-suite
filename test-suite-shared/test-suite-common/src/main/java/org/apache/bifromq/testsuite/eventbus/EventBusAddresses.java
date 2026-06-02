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

package org.apache.bifromq.testsuite.eventbus;

import org.apache.bifromq.testsuite.Constants;

public final class EventBusAddresses {

    public static final String CLUSTER_TASK_COMMAND = Constants.CLUSTER_TASK_MESSAGE;
    public static final String TASK_STATE_CHANGE = Constants.TASK_STATE_CHANGE_EVENT;
    public static final String PIPELINE_PROGRESS = Constants.PIPELINE_PROGRESS_EVENT;

    private EventBusAddresses() {
    }

    public static String nodeMetrics(String nodeId) {
        return Constants.NODE_METRICS_ADDRESS_PREFIX + nodeId + ".metrics";
    }

    public static String nodeClients(String nodeId) {
        return Constants.NODE_METRICS_ADDRESS_PREFIX + nodeId + ".clients";
    }

    public static String localPortCapacity(String nodeId) {
        return Constants.NODE_METRICS_ADDRESS_PREFIX + nodeId + Constants.NODE_LOCAL_PORT_CAPACITY_SUFFIX;
    }

    public static String taskMetricsCleanup(String nodeId) {
        return Constants.NODE_METRICS_ADDRESS_PREFIX + nodeId + Constants.NODE_TASK_METRICS_CLEANUP_SUFFIX;
    }

    public static String workerCommand(String nodeId) {
        return "worker." + nodeId + ".command";
    }
}
