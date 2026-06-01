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

// Cluster management API service
import {api} from '../../../utils/request';
import type {LocalPortModeConfig, NodeListVO, NodeTaskInfo} from '../domain';

export const clusterApi = {
    // Get all node info
    getAllNodes: () => {
        return api.get<NodeListVO[]>('/node/allNodes');
    },

    // Get single node info
    getNode: (nodeId: string) => {
        return api.get<NodeListVO>('/node/:nodeId', {params: {nodeId}});
    },

    // Get task list running on node
    getNodeTasks: (nodeId: string) => {
        return api.get<NodeTaskInfo[]>('/node/:nodeId/tasks', {params: {nodeId}});
    },

    getLocalPortMode: () => {
        return api.get<LocalPortModeConfig>('/cluster/config/local-port-mode');
    },

    updateLocalPortMode: (config: LocalPortModeConfig) => {
        return api.put<LocalPortModeConfig>('/cluster/config/local-port-mode', config);
    },
};

export default clusterApi;
