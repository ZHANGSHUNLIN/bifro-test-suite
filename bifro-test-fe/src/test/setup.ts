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

import '@testing-library/jest-dom/vitest'
import {cleanup} from '@testing-library/react'
import {afterAll, afterEach, beforeAll} from 'vitest'
import {setupServer} from 'msw/node'
import {delay, http, HttpResponse} from 'msw'
import type {TaskConfig, TaskDetailResponse, TaskListItem} from '../features/task'
import {TaskStatusValues, TaskTypeValues} from '../features/task'

// Mock data
const mockTask: TaskListItem = {
    id: '1',
    taskId: 'task1234',
    taskName: 'Test Task',
    taskType: TaskTypeValues.PUBSUB,
    protocol: 'mqtt',
    group: 'group1',
    brokers: [{host: 'localhost', port: 1883, brokerId: 'broker1'}],
    totalClientCount: 1000,
    status: TaskStatusValues.ONGOING,
    createTime: '2024-01-01T00:00:00Z',
}

const mockTaskConfig: TaskConfig = {
    taskId: 'task1234',
    taskType: TaskTypeValues.PUBSUB,
    template: 'PUBSUB_STANDARD',
    protocol: 'mqtt',
    group: 'group1',
    brokers: [mockTask.brokers[0]!],
    port: 1883,
    totalClientCount: 1000,
    topic: 'test/topic',
    qos: 1,
    messageSize: 100,
    publishRate: 1,
    stressDurationInSec: 60,
    keepAliveInSec: 120,
    taskWorkStage: TaskStatusValues.ONGOING,
}

const mockTaskDetailResponse: TaskDetailResponse = {
    success: true,
    message: 'Success',
    taskId: 'task1234',
    taskName: 'Test Task',
    group: 'group1',
    mainTaskView: {
        taskId: 'task1234',
        taskType: TaskTypeValues.PUBSUB,
        template: 'PUBSUB_STANDARD',
        totalClientCount: 1000,
        stressDurationInSec: 60,
        taskWorkStage: TaskStatusValues.ONGOING,
    },
    brokers: [mockTask.brokers[0]!],
    subTasks: {
        node1: mockTaskConfig,
    },
    subTaskDetails: {
        node1: {
            nodeId: 'node1',
            nodeName: 'test-node-1',
            taskType: 'PUBSUB',
            totalClientCount: 1000,
            taskWorkStage: 'ONGOING',
        },
    },
    statistics: {
        totalNodes: 1,
        totalAssignedClients: 1000,
        minClientsPerNode: 1000,
        maxClientsPerNode: 1000,
        averageClientsPerNode: 1000,
        distributionBalance: 1.0,
    },
    timestamp: Date.now(),
    createTime: Date.now() - 30 * 1000,
}
export const server = setupServer(
    // GET /api/task/list
    http.get('http://localhost:8081/api/task/list', async ({request}) => {
        const url = new URL(request.url)
        const pageNum = url.searchParams.get('pageNum')
        await delay(100)
        return HttpResponse.json({
            code: 200,
            data: {
                content: [mockTask],
                totalElements: 1,
                totalPages: 1,
                size: pageNum === '2' ? 50 : 20,
                number: pageNum ? parseInt(pageNum) - 1 : 0,
                numberOfElements: 1,
                first: pageNum !== '2',
                last: !pageNum,
            },
        })
    }),

    // GET /api/task/templates (must come before :id)
    http.get('http://localhost:8081/api/task/templates', async () => {
        await delay(50)
        return HttpResponse.json({
            code: 200,
            data: [
                {value: 'CONN_STANDARD', label: 'Connection Standard Template', type: 'CONN'},
                {value: 'PUBSUB_STANDARD', label: 'PubSub Standard Template', type: 'PUBSUB'},
            ],
        })
    }),

    // GET /api/node/metrics
    http.get('http://localhost:8081/api/node/metrics', async ({request}) => {
        const url = new URL(request.url)
        const nodeId = url.searchParams.get('nodeId')
        await delay(100)
        if (nodeId === 'offline-node') {
            return HttpResponse.json({
                code: 200,
                data: {
                    nodeId: 'offline-node',
                    success: false,
                    errorCode: 'NODE_OFFLINE',
                    errorMessage: 'Node not found or offline',
                    timestamp: Date.now(),
                    counterMetrics: [],
                    timerMetrics: [],
                },
            })
        }
        return HttpResponse.json({
            code: 200,
            data: {
                nodeId: 'node1',
                success: true,
                timestamp: Date.now(),
                counterMetrics: [
                    {name: 'bifro_task_metric_connect_success_count', tags: {taskId: 'task1234'}, count: 1000},
                    {name: 'bifro_task_metric_connect_exception_count', tags: {taskId: 'task1234'}, count: 5},
                ],
                timerMetrics: [
                    {
                        name: 'bifro_task_metric_connect_latency',
                        tags: {taskId: 'task1234'},
                        count: 100,
                        mean: 12.5,
                        p50: 10.0,
                        p95: 25.0,
                        p99: 45.0,
                        max: 120.0,
                        totalTime: 1250.0,
                        hasData: true
                    },
                ],
            },
        })
    }),

    // GET /api/task/:id
    http.get('http://localhost:8081/api/task/:id', async ({params}) => {
        await delay(50)
        if (params.id === 'not-found') {
            return HttpResponse.json({code: 404, message: '404'}, {status: 404})
        }
        return HttpResponse.json({
            code: 200,
            data: mockTaskDetailResponse,
        })
    }),

    // POST /api/task
    http.post('http://localhost:8081/api/task', async ({request}) => {
        const body = await request.json() as Record<string, unknown>
        await delay(100)
        if (!body.taskType) {
            return HttpResponse.json({code: 400, message: '400'}, {status: 400})
        }
        const newTask: TaskConfig = {
            ...mockTaskConfig,
            taskId: 'new' + Math.random().toString(36).substring(7),
            taskType: body.taskType as 'CONN' | 'PUBSUB',
            totalClientCount: (body.totalClientCount as number) || 100,
        }
        return HttpResponse.json({
            code: 200,
            data: {...newTask, taskName: body.taskName || 'New Task'},
        }, {status: 201})
    }),

    // PUT /api/task/:id
    http.put('http://localhost:8081/api/task/:id', async ({params, request}) => {
        await delay(100)
        if (params.id === 'not-found') {
            return HttpResponse.json({code: 404, message: '404'}, {status: 404})
        }
        const body = await request.json() as Record<string, unknown>
        return HttpResponse.json({
            code: 200,
            data: {...mockTaskConfig, taskName: body.taskName},
        })
    }),

    // POST /api/task/:id/confirmTask
    http.post('http://localhost:8081/api/task/:id/confirmTask', async ({params}) => {
        await delay(100)
        if (params.id === 'not-found') {
            return HttpResponse.json({code: 404, message: '404'}, {status: 404})
        }
        return HttpResponse.json({
            code: 200,
            data: mockTaskDetailResponse,
        })
    }),

    // POST /api/task/assign/:taskId
    http.post('http://localhost:8081/api/task/assign/:taskId', async ({params}) => {
        await delay(100)
        if (params.taskId === 'not-found') {
            return HttpResponse.json({code: 404, message: '404'}, {status: 404})
        }
        return HttpResponse.json({
            code: 200,
            data: mockTaskConfig,
        })
    }),

    // POST /api/task/calculate/:taskId
    http.post('http://localhost:8081/api/task/calculate/:taskId', async ({params}) => {
        await delay(100)
        if (params.taskId === 'error') {
            return HttpResponse.json({code: 500, message: '500'}, {status: 500})
        }
        return HttpResponse.json({
            code: 200,
            data: {
                totalClientCount: 1000,
                nodeAllocationList: [
                    {nodeId: 'node1', allocatedClientCount: 500},
                    {nodeId: 'node2', allocatedClientCount: 500},
                ],
            },
        })
    }),

    // DELETE /api/task/batch (must come before :id)
    http.delete('http://localhost:8081/api/task/batch', async ({request}) => {
        const body = await request.json() as unknown
        await delay(100)
        if (!body || !Array.isArray(body) || body.length === 0) {
            return HttpResponse.json({code: 400, message: '400'}, {status: 400})
        }
        return HttpResponse.json({
            code: 200,
            data: `Deleted ${body.length} tasks`,
        })
    }),

    // DELETE /api/task/:id
    http.delete('http://localhost:8081/api/task/:id', async ({params}) => {
        await delay(100)
        if (params.id === 'not-found') {
            return HttpResponse.json({code: 404, message: '404'}, {status: 404})
        }
        return HttpResponse.json({
            code: 200,
            data: mockTaskDetailResponse,
        })
    }),

    // POST /api/task/stop/:id
    http.post('http://localhost:8081/api/task/stop/:id', async ({params}) => {
        await delay(100)
        if (params.id === 'not-found') {
            return HttpResponse.json({code: 404, message: '404'}, {status: 404})
        }
        if (params.id === 'cannot-stop') {
            return HttpResponse.json({code: 400, message: '400'}, {status: 400})
        }
        return HttpResponse.json({
            code: 200,
            data: 'Task stopped successfully',
        })
    }),
)

// Set up MSW server
beforeAll(() => server.listen({onUnhandledRequest: 'bypass'}))

// Reset handlers and DOM after each test
afterEach(() => {
    server.resetHandlers()
    cleanup()
})

// Close server after all tests
afterAll(() => server.close())

// Mock window.matchMedia (required by Ant Design)
Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {
        },
        removeListener: () => {
        },
        addEventListener: () => {
        },
        removeEventListener: () => {
        },
        dispatchEvent: () => false,
    }),
})

// Mock ResizeObserver (required by Ant Design Table)
global.ResizeObserver = class ResizeObserver {
    observe() {
    }

    unobserve() {
    }

    disconnect() {
    }
}

// Mock window.getComputedStyle (required by Ant Design Table)
Object.defineProperty(window, 'getComputedStyle', {
    writable: true,
    value: () => ({
        getPropertyValue: () => '',
        get: () => '',
        cssText: '',
    }),
})

// Mock scrollIntoView
Element.prototype.scrollIntoView = () => {
}
