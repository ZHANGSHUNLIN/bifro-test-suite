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

package org.apache.bifromq.testsuite;

public final class Constants {

    public static final String CLUSTER_TASK_MESSAGE = "cluster.task.message";
    public static final String NODE_METRICS_ADDRESS_PREFIX = "node.";
    public static final String NODE_LOCAL_PORT_CAPACITY_SUFFIX = ".local-port-capacity";
    public static final String TASK_STATE_CHANGE_EVENT = "task.state.change";
    
    public static final String PIPELINE_PROGRESS_EVENT = "task.pipeline.progress";
    public static final String CONN_CLIENT_TAG = "CONN_CLIENTS";
    public static final String PUB_CLIENT_TAG = "PUB_CLIENTS";
    public static final String SUB_CLIENT_TAG = "SUB_CLIENTS";

    private Constants() {
    }

}
