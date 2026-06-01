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
import type {TaskListItem} from '../../../features/task'
import {
    mockNodeTaskAllocationVO,
    mockTaskConfig,
    mockTaskDetailResponse,
    mockTaskListResponse,
    mockTemplates,
} from '../data/task'

export default [
    // GET /task/list - Get Task List
    http.get('/api/task/list', async ({request}) => {
        const url = new URL(request.url)
        const taskName = url.searchParams.get('taskName')
        const taskType = url.searchParams.get('taskType')
        const group = url.searchParams.get('group')

        await delay(100)

        // Simple filter logic
        let content = [...mockTaskListResponse.content] as TaskListItem[]
        if (taskName) {
            content = content.filter((t) => t.taskName?.includes(taskName))
        }
        if (taskType) {
            content = content.filter((t) => t.taskType === taskType)
        }
        if (group) {
            content = content.filter((t) => t.group === group)
        }

        return HttpResponse.json({
            code: 200,
            data: {...mockTaskListResponse, content, numberOfElements: content.length},
        })
    }),

    // GET /task/:id - get task details
    http.get('/api/task/:id', async ({params}) => {
        const {id} = params
        await delay(50)

        if (id === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Task not found'}, {status: 404})
        }

        return HttpResponse.json({
            code: 200,
            data: mockTaskDetailResponse,
        })
    }),

    // GET /task/:id/diagnostics - get troubleshooting diagnostics
    http.get('/api/task/:id/diagnostics', async ({params}) => {
        const {id} = params
        await delay(50)

        if (id === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Task not found'}, {status: 404})
        }

        return HttpResponse.json({
            code: 200,
            data: {
                taskId: id,
                generatedAt: Date.now(),
                window: {
                    startMs: mockTaskDetailResponse.startTime,
                    endMs: mockTaskDetailResponse.endTime ?? Date.now(),
                },
                taskSnapshot: mockTaskDetailResponse,
                subtasks: Object.values(mockTaskDetailResponse.subTaskDetails ?? {}),
                stateHistory: [],
                pipelineDiagnostics: Object.values(mockTaskDetailResponse.subTaskDetails ?? {}).map((subtask) => ({
                    nodeId: subtask.nodeId,
                    nodeName: subtask.nodeName,
                    taskType: subtask.taskType,
                    taskWorkStage: subtask.taskWorkStage,
                    totalClientCount: subtask.totalClientCount,
                    stages: subtask.pipelineStages ?? [],
                })),
                symptoms: [],
                nextActions: [],
                logFiles: ['task-pipeline.log', 'task-state-machine.log', 'conn.log', 'error.log', `task/${id}.log`],
                logQueryKeys: [`taskId=${id}`],
            },
        })
    }),

    // GET /task/:id/log-summary - get whitelisted task log lines
    http.get('/api/task/:id/log-summary', async ({params, request}) => {
        const {id} = params
        const url = new URL(request.url)
        const lines = Number(url.searchParams.get('lines') ?? '200')
        await delay(50)

        if (id === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Task not found'}, {status: 404})
        }

        const entries = [
            {file: 'task-pipeline.log', line: `taskId=${id} event=stage_start stage=StartConnClients-CONN_CLIENTS`},
            {file: 'conn.log', line: `taskId=${id} nodeId=node-1 reasonSummary=connect_timeout:2`},
            {file: `task/${id}.log`, line: `taskId=${id} event=task_progress`},
        ].slice(-Math.max(1, Math.min(lines, 1000)))

        return HttpResponse.json({
            code: 200,
            data: {
                taskId: id,
                generatedAt: Date.now(),
                files: ['task-pipeline.log', 'conn.log', `task/${id}.log`],
                lines: entries,
            },
        })
    }),

    // POST /task - add task
    http.post('/api/task', async ({request}) => {
        const body = await request.json() as Record<string, unknown>
        await delay(100)

        // Validate required fields
        if (!body.taskType) {
            return HttpResponse.json({code: 400, message: 'taskType is required'}, {status: 400})
        }

        const newTask = {
            ...mockTaskConfig,
            taskId: 'new' + Math.random().toString(36).substring(7),
            taskType: body.taskType as 'CONN' | 'PUBSUB',
            taskName: body.taskName || 'New Task',
        }

        return HttpResponse.json({
            code: 200,
            data: newTask,
        }, {status: 201})
    }),

    // PUT /task/:id - update task
    http.put('/api/task/:id', async ({params, request}) => {
        const {id} = params
        const body = await request.json() as Record<string, unknown>
        await delay(100)

        if (id === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Task not found'}, {status: 404})
        }

        const updatedTask = {
            ...mockTaskConfig,
            taskId: id as string,
            taskName: body.taskName,
            taskType: body.taskType as 'CONN' | 'PUBSUB',
        }

        return HttpResponse.json({
            code: 200,
            data: updatedTask,
        })
    }),

    // POST /task/:id/confirmTask - confirm task
    http.post('/api/task/:id/confirmTask', async ({params}) => {
        const {id} = params
        await delay(100)

        if (id === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Task not found'}, {status: 404})
        }

        return HttpResponse.json({
            code: 200,
            data: mockTaskDetailResponse,
        })
    }),

    // POST /task/assign/:taskId - assign task
    http.post('/api/task/assign/:taskId', async ({params}) => {
        const {taskId} = params
        await delay(100)

        if (taskId === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Task not found'}, {status: 404})
        }

        return HttpResponse.json({
            code: 200,
            data: mockTaskConfig,
        })
    }),

    // POST /task/calculate/:taskId - calculate task allocation
    http.post('/api/task/calculate/:taskId', async ({params}) => {
        const {taskId} = params
        await delay(100)

        if (taskId === 'error') {
            return HttpResponse.json({code: 500, message: 'Calculation failed'}, {status: 500})
        }

        return HttpResponse.json({
            code: 200,
            data: mockNodeTaskAllocationVO,
        })
    }),

    // DELETE /task/:id - delete task
    http.delete('/api/task/:id', async ({params}) => {
        const {id} = params
        await delay(100)

        if (id === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Task not found'}, {status: 404})
        }

        return HttpResponse.json({
            code: 200,
            data: mockTaskDetailResponse,
        })
    }),

    // DELETE /task/batch - batch delete tasks
    http.delete('/api/task/batch', async ({request}) => {
        const body = await request.json() as unknown
        await delay(100)

        if (!Array.isArray(body) || body.length === 0) {
            return HttpResponse.json({code: 400, message: 'Invalid request body'}, {status: 400})
        }

        return HttpResponse.json({
            code: 200,
            data: `Deleted ${body.length} tasks`,
        })
    }),

    // POST /task/stop/:id - Stop Task
    http.post('/api/task/stop/:id', async ({params}) => {
        const {id} = params
        await delay(100)

        if (id === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Task not found'}, {status: 404})
        }

        if (id === 'cannot-stop') {
            return HttpResponse.json({code: 400, message: 'Cannot stop task in current state'}, {status: 400})
        }

        return HttpResponse.json({
            code: 200,
            data: 'Task stopped successfully',
        })
    }),

    // GET /task/templates - get template list
    http.get('/api/task/templates', async () => {
        await delay(50)
        return HttpResponse.json({
            code: 200,
            data: mockTemplates,
        })
    }),
] as const
