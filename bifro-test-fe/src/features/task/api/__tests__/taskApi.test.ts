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

import {describe, expect, it} from 'vitest'
import {taskApi} from '../taskApi'
import {TaskTypeValues} from '../../domain'

// Contract tests depend on real backend service and specific test data, only run when VITEST_CONTRACT=true
const runContract = process.env.VITEST_CONTRACT === 'true'

describe.skipIf(!runContract)('taskApi | contract tests', () => {
    describe('getAllTasks', () => {
        it('getAllTasks_withNoParams_returnsTaskList', async () => {
            // when
            const response = await taskApi.getAllTasks()

            // then - verify response structure matches PageInfo
            expect(response).toHaveProperty('content')
            expect(response).toHaveProperty('totalElements')
            expect(response).toHaveProperty('totalPages')
            expect(response).toHaveProperty('size')
            expect(response).toHaveProperty('number')
            expect(response.content).toHaveLength(1)
            expect(response.content[0]).toHaveProperty('taskId')
            expect(response.content[0]).toHaveProperty('taskType')
            expect(response.content[0]).toHaveProperty('status')
        })

        it('getAllTasks_withTaskName_returnsFilteredTasks', async () => {
            // when
            const response = await taskApi.getAllTasks('Test Task')

            // then
            expect(response.content).toHaveLength(1)
            expect(response.content[0]?.taskName).toContain('Test Task')
        })

        it('getAllTasks_withTaskType_returnsFilteredTasks', async () => {
            // when
            const response = await taskApi.getAllTasks(undefined, TaskTypeValues.PUBSUB)

            // then
            expect(response.content[0]?.taskType).toBe(TaskTypeValues.PUBSUB)
        })

        it('getAllTasks_withGroup_returnsFilteredTasks', async () => {
            // when
            const response = await taskApi.getAllTasks(undefined, undefined, 'group1')

            // then
            expect(response.content[0]?.group).toBe('group1')
        })

        it('getAllTasks_withPagination_returnsPagedResult', async () => {
            // when
            const response = await taskApi.getAllTasks(undefined, undefined, undefined, undefined, 2, 50)

            // then
            expect(response.number).toBe(1) // second page (0-indexed)
            expect(response.size).toBe(50)
        })
    })

    describe('getTaskDetails', () => {
        it('getTaskDetails_withValidId_returnsTaskDetail', async () => {
            // when
            const response = await taskApi.getTaskDetails('task1234')

            // then - verify response structure matches TaskDetailResponse
            expect(response).toHaveProperty('success')
            expect(response).toHaveProperty('taskId')
            expect(response).toHaveProperty('taskName')
            expect(response).toHaveProperty('mainTaskView')
            expect(response).toHaveProperty('brokers')
            expect(response).toHaveProperty('subTasks')
            expect(response.taskId).toBe('task1234')
        })

        it('getTaskConfig_withValidId_returnsFullTaskConfig', async () => {
            // when
            const response = await taskApi.getTaskConfig('task1234')

            // then - full config is available only from the explicit config endpoint
            expect(response).toHaveProperty('taskId')
            expect(response).toHaveProperty('taskType')
        })

        it('getTaskDetails_withNotFoundId_throwsError', async () => {
            // then
            await expect(taskApi.getTaskDetails('not-found')).rejects.toThrow('404')
        })
    })

    describe('addTask', () => {
        it('addTask_withValidTaskRequest_returnsTaskConfig', async () => {
            // given
            const taskRequest = {
                taskName: 'New Task',
                taskType: TaskTypeValues.PUBSUB,
                protocol: 'mqtt',
                brokers: [{host: 'localhost', port: 1883}],
                totalClientCount: 100,
            }

            // when
            const response = await taskApi.addTask(taskRequest)

            // then - verify response structure matches TaskConfig
            expect(response).toHaveProperty('taskId')
            expect(response).toHaveProperty('taskType')
            expect(response.taskType).toBe(TaskTypeValues.PUBSUB)
            expect(response.totalClientCount).toBe(100)
        })

        it('addTask_withMissingTaskType_throwsError', async () => {
            // given
            const taskRequest = {
                taskName: 'Invalid Task',
            }

            // then
            await expect(taskApi.addTask(taskRequest)).rejects.toThrow('400')
        })
    })

    describe('updateTask', () => {
        it('updateTask_withValidIdAndRequest_returnsUpdatedTask', async () => {
            // given
            const taskRequest = {
                taskName: 'Updated Task',
                taskType: TaskTypeValues.PUBSUB,
            }

            // when
            const response = await taskApi.updateTask('task1234', taskRequest)

            // then
            expect(response).toHaveProperty('taskId')
        })

        it('updateTask_withNotFoundId_throwsError', async () => {
            // given
            const taskRequest = {taskName: 'Updated Task'}

            // then
            await expect(taskApi.updateTask('not-found', taskRequest)).rejects.toThrow('404')
        })
    })

    describe('confirmTask', () => {
        it('confirmTask_withValidId_returnsTaskDetail', async () => {
            // when
            const response = await taskApi.confirmTask('task1234')

            // then
            expect(response).toHaveProperty('success')
            expect(response.success).toBe(true)
        })

        it('confirmTask_withNotFoundId_throwsError', async () => {
            // then
            await expect(taskApi.confirmTask('not-found')).rejects.toThrow('404')
        })
    })

    describe('assignTask', () => {
        it('assignTask_withValidIdAndAllocation_returnsTaskConfig', async () => {
            // given
            const allocationRequest = {
                totalClientCount: 1000,
                nodeAllocationList: [
                    {nodeId: 'node1', allocatedClientCount: 500},
                    {nodeId: 'node2', allocatedClientCount: 500},
                ],
            }

            // when
            const response = await taskApi.assignTask('task1234', allocationRequest)

            // then
            expect(response).toHaveProperty('taskId')
        })

        it('assignTask_withNotFoundId_throwsError', async () => {
            // then
            await expect(taskApi.assignTask('not-found')).rejects.toThrow('404')
        })
    })

    describe('calculateNodeTaskAllocation', () => {
        it('calculateNodeTaskAllocation_withValidId_returnsAllocation', async () => {
            // when
            const response = await taskApi.calculateNodeTaskAllocation('task1234')

            // then - verify response structure matches NodeTaskAllocationVO
            expect(response).toHaveProperty('totalClientCount')
            expect(response).toHaveProperty('nodeAllocationList')
            expect(response.totalClientCount).toBe(1000)
            expect(response.nodeAllocationList).toHaveLength(2)
            expect(response.nodeAllocationList[0]).toHaveProperty('nodeId')
            expect(response.nodeAllocationList[0]).toHaveProperty('allocatedClientCount')
        })

        it('calculateNodeTaskAllocation_withErrorId_throwsError', async () => {
            // then
            await expect(taskApi.calculateNodeTaskAllocation('error')).rejects.toThrow('500')
        })
    })

    describe('deleteTask', () => {
        it('deleteTask_withValidId_returnsTaskDetail', async () => {
            // when
            const response = await taskApi.deleteTask('task1234')

            // then
            expect(response).toHaveProperty('taskId')
        })

        it('deleteTask_withNotFoundId_throwsError', async () => {
            // then
            await expect(taskApi.deleteTask('not-found')).rejects.toThrow('404')
        })
    })

    describe('batchDeleteTask', () => {
        it('batchDeleteTask_withValidIds_returnsSuccessMessage', async () => {
            // given
            const ids = ['task1', 'task2', 'task3']

            // when
            const response = await taskApi.batchDeleteTask(ids)

            // then
            expect(response).toBe('Deleted 3 tasks')
        })

        it('batchDeleteTask_withEmptyArray_throwsError', async () => {
            // then
            await expect(taskApi.batchDeleteTask([])).rejects.toThrow('400')
        })
    })

    describe('stopTask', () => {
        it('stopTask_withValidId_returnsSuccessMessage', async () => {
            // when
            const response = await taskApi.stopTask('task1234')

            // then
            expect(response).toContain('stopped successfully')
        })

        it('stopTask_withNotFoundId_throwsError', async () => {
            // then
            await expect(taskApi.stopTask('not-found')).rejects.toThrow('404')
        })

        it('stopTask_withCannotStopState_throwsError', async () => {
            // then
            await expect(taskApi.stopTask('cannot-stop')).rejects.toThrow('400')
        })
    })

    describe('getTemplates', () => {
        it('getTemplates_returnsTemplateList', async () => {
            // when
            const response = await taskApi.getTemplates()

            // then - verify response structure matches template list
            expect(Array.isArray(response)).toBe(true)
            expect(response).toHaveLength(2)
            expect(response[0]).toHaveProperty('value')
            expect(response[0]).toHaveProperty('label')
            expect(response[0]).toHaveProperty('type')
            expect(response[0]?.type).toBe('CONN')
            expect(response[1]?.type).toBe('PUBSUB')
        })
    })
})
