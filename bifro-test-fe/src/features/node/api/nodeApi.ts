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

// Node management API service
import {api} from '../../../utils/request';
import type {ClientInstanceResponse, NodeMetricsResponse} from '../../task';

export const nodeApi = {
    // Get node real-time metrics
    getNodeMetrics: (nodeId: string, taskId?: string, metricNames?: string[]) => {
        const params: Record<string, string | number> = {nodeId};
        if (taskId) params.taskId = taskId;
        if (metricNames && metricNames.length > 0) params.metricNames = metricNames.join(',');
        return api.get<NodeMetricsResponse>('/node/metrics', {params});
    },

    // Query client instances (paginated)
    getClientInstances: (nodeId: string, taskId: string, clientType: string = 'conn', page: number = 0, size: number = 20) => {
        const params: Record<string, string | number> = {nodeId, taskId, clientType, page, size};
        return api.get<ClientInstanceResponse>('/node/:nodeId/clients', {params});
    },
};

export default nodeApi;
