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

import type {
    NodeMetricsResponse,
    PageInfo,
    TaskConfig,
    TaskDetailResponse,
    TaskListItem,
} from '../../../features/task'
import {TaskStatusValues, TaskTypeValues} from '../../../features/task'

// Mock task list item
export const mockTaskListItem: TaskListItem = {
    id: '1',
    taskId: 'task1234',
    taskName: 'Test Task',
    taskType: TaskTypeValues.PUBSUB,
    protocol: 'mqtt',
    group: 'group1',
    brokers: [
        {
            host: 'localhost',
            port: 1883,
            brokerId: 'broker1',
            name: 'Test Broker',
            enabled: true,
        },
    ],
    totalClientCount: 1000,
    status: TaskStatusValues.ONGOING,
    nodeId: 'node1',
    createTime: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T01:00:00Z',
}

// Mock task list response
export const mockTaskListResponse: PageInfo<TaskListItem> = {
    content: [mockTaskListItem],
    totalElements: 1,
    totalPages: 1,
    size: 20,
    number: 0,
    numberOfElements: 1,
    first: true,
    last: true,
}

// Mock task config
export const mockTaskConfig: TaskConfig = {
    taskId: 'task1234',
    taskType: TaskTypeValues.PUBSUB,
    template: 'PUBSUB_STANDARD',
    protocol: 'mqtt',
    group: 'group1',
    brokers: [mockTaskListItem.brokers[0]!],
    port: 1883,
    totalClientCount: 1000,
    topic: 'test/topic',
    qos: 1,
    messageSize: 100,
    publishRate: 1,
    stressDurationInSec: 60,
    keepAliveInSec: 120,
    taskWorkStage: TaskStatusValues.ONGOING,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T01:00:00Z',
}

// Mock task detail response
export const mockTaskDetailResponse: TaskDetailResponse = {
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
    brokers: [mockTaskListItem.brokers[0]!],
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
    createTime: Date.now() - 30 * 1000, // created 30 seconds ago
}
// Mock template list
export const mockTemplates = [
    {value: 'CONN_STANDARD', label: 'Connection Standard Template', type: 'CONN'},
    {value: 'PUBSUB_STANDARD', label: 'PubSub Standard Template', type: 'PUBSUB'},
]

// Mock node metrics response
export const mockNodeMetricsResponse: NodeMetricsResponse = {
    nodeId: 'node1',
    success: true,
    timestamp: Date.now(),
    counterMetrics: [
        {name: 'bifro_task_metric_connect_success_count', tags: {taskId: 'task1234'}, count: 1000},
        {name: 'bifro_task_metric_connect_exception_count', tags: {taskId: 'task1234'}, count: 5},
        {name: 'bifro_task_metric_publish_count', tags: {taskId: 'task1234'}, count: 5000},
        {name: 'bifro_task_metric_message_received_count', tags: {taskId: 'task1234'}, count: 4500},
    ],
    timerMetrics: [
        {
            name: 'bifro_task_metric_connect_latency',
            tags: {taskId: 'task1234'},
            count: 1000,
            mean: 12.5,
            p50: 10.0,
            p95: 25.0,
            p99: 45.0,
            max: 120.0,
            totalTime: 12500.0,
            hasData: true
        },
        {
            name: 'bifro_task_metric_publish_latency',
            tags: {taskId: 'task1234', qos: '1'},
            count: 5000,
            mean: 3.2,
            p50: 2.5,
            p95: 8.0,
            p99: 15.0,
            max: 50.0,
            totalTime: 16000.0,
            hasData: true
        },
    ],
}

export const mockNodeMetricsOfflineResponse: NodeMetricsResponse = {
    nodeId: 'offline-node',
    success: false,
    errorCode: 'NODE_OFFLINE',
    errorMessage: 'Node not found or offline',
    timestamp: Date.now(),
    counterMetrics: [],
    timerMetrics: [],
}
