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

import {delay, http, HttpResponse} from 'msw'
import type {PageInfo} from '../../../features/task'
import {mockNodeMetricsOfflineResponse, mockNodeMetricsResponse,} from '../data/task'

// Mock node info (custom type, not using NodeListVO due to different structure)
interface MockClusterNodeInfo {
    nodeId: string;
    host: string;
    port: number;
    status: string;
    cpu: number;
    memory: number;
    lastHeartbeat: string;
}

export const mockClusterNodeInfo: MockClusterNodeInfo = {
    nodeId: 'node1',
    host: '192.168.1.100',
    port: 8080,
    status: 'active',
    cpu: 50.5,
    memory: 60.2,
    lastHeartbeat: '2024-01-01T00:00:00Z',
}

export const mockNodeListResponse: PageInfo<MockClusterNodeInfo> = {
    content: [mockClusterNodeInfo],
    totalElements: 1,
    totalPages: 1,
    size: 20,
    number: 0,
    numberOfElements: 1,
    first: true,
    last: true,
}

export default [
    // GET /node/allNodes - Get All Nodes
    http.get('/api/node/allNodes', async () => {
        await delay(100)
        return HttpResponse.json({
            code: 200,
            data: [mockClusterNodeInfo],
        })
    }),

    http.get('/api/cluster/config/local-port-mode', async () => {
        await delay(20)
        return HttpResponse.json({
            code: 200,
            data: {
                enabled: false,
                startPort: 10000,
                endPort: 65535,
            },
        })
    }),

    http.put('/api/cluster/config/local-port-mode', async ({request}) => {
        const body = await request.json()
        await delay(20)
        return HttpResponse.json({
            code: 200,
            data: body,
        })
    }),

    // GET /node/:nodeId - get single node details
    http.get('/api/node/:nodeId', async ({params}) => {
        const {nodeId} = params
        await delay(50)

        if (nodeId === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Node not found'}, {status: 404})
        }

        return HttpResponse.json({
            code: 200,
            data: mockClusterNodeInfo,
        })
    }),

    // GET /node/metrics - get node real-time metrics
    http.get('/api/node/metrics', async ({request}) => {
        const url = new URL(request.url)
        const nodeId = url.searchParams.get('nodeId')
        await delay(100)

        if (nodeId === 'offline-node') {
            return HttpResponse.json({code: 200, data: mockNodeMetricsOfflineResponse})
        }

        return HttpResponse.json({
            code: 200,
            data: mockNodeMetricsResponse,
        })
    }),
] as const
