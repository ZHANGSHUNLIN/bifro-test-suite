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

import {beforeEach, describe, expect, it, vi} from 'vitest'
import {render, screen, waitFor} from '@testing-library/react'
import TaskDetailModal from '../TaskDetailModal'
import groupApi from '../../../features/group'

const mockListProfiles = vi.hoisted(() => vi.fn().mockResolvedValue([]))

const mockTaskConfig = {
    taskType: 'PUBSUB',
    template: 'PUBSUB_STANDARD',
    protocol: 'mqtt',
    totalClientCount: 1000,
    connectRate: 100,
    disconnectRate: 2000,
    fanOut: 1,
    fanIn: 1,
    topic: 'test/topic',
    qos: 1,
    messageSize: 32,
    publishRate: 1,
    stressDurationInSec: 60,
    cleanSession: true,
    retain: false,
    mqtt5: false,
    authType: 'normal',
    taskWorkStage: 'ONGOING',
}

// Mock useTaskData hook
const mockLoadTaskBasicInfo = vi.fn().mockResolvedValue({
    taskId: 'task1234',
    taskName: 'Test Task',
    group: 'group1',
    mainTaskView: {
        taskType: 'PUBSUB',
        template: 'PUBSUB_STANDARD',
        totalClientCount: 1000,
        stressDurationInSec: 60,
        taskWorkStage: 'ONGOING',
    },
    brokers: [{host: 'localhost', port: 1883}],
    createTime: Date.now() - 30 * 1000,
})
const mockLoadTaskConfig = vi.fn().mockResolvedValue(mockTaskConfig)

const mockLoadTaskStatistics = vi.fn().mockResolvedValue({
    taskId: 'task1234',
    metricsFromSnapshot: false,
    statistics: {
        totalNodes: 1,
        totalAssignedClients: 1000,
        minClientsPerNode: 1000,
        maxClientsPerNode: 1000,
    },
})

const mockLoadTaskSubTasks = vi.fn().mockResolvedValue({
    taskId: 'task1234',
    subTasks: {
        node1: {
            taskType: 'PUBSUB',
            totalClientCount: 1000,
            taskWorkStage: 'ONGOING',
        },
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
})

vi.mock('../../../features/task/model', () => ({
    useTaskData: () => ({
        loadTaskBasicInfo: mockLoadTaskBasicInfo,
        loadTaskConfig: mockLoadTaskConfig,
        loadTaskStatistics: mockLoadTaskStatistics,
        loadTaskSubTasks: mockLoadTaskSubTasks,
    }),
}))

// Mock groupApi
vi.mock('../../../features/group', () => ({
    default: {
        getAllGroupsForSelect: vi.fn().mockResolvedValue([]),
    },
}))

vi.mock('../../../features/profile', () => ({
    listProfiles: mockListProfiles,
}))

const mockGroupApi = vi.mocked(groupApi)

describe('TaskDetailModal | component tests', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockLoadTaskBasicInfo.mockResolvedValue({
            taskId: 'task1234',
            taskName: 'Test Task',
            group: 'group1',
            mainTaskView: {
                taskType: 'PUBSUB',
                template: 'PUBSUB_STANDARD',
                totalClientCount: 1000,
                stressDurationInSec: 60,
                taskWorkStage: 'ONGOING',
            },
            brokers: [{host: 'localhost', port: 1883}],
        })
        mockLoadTaskConfig.mockResolvedValue(mockTaskConfig)
        mockGroupApi.getAllGroupsForSelect.mockResolvedValue([])
        mockListProfiles.mockResolvedValue([])
        mockLoadTaskStatistics.mockResolvedValue({
            taskId: 'task1234',
            metricsFromSnapshot: false,
            statistics: {totalNodes: 1, totalAssignedClients: 1000, minClientsPerNode: 1000, maxClientsPerNode: 1000},
        })
        mockLoadTaskSubTasks.mockResolvedValue({
            taskId: 'task1234',
            subTasks: {node1: {taskType: 'PUBSUB', totalClientCount: 1000, taskWorkStage: 'ONGOING'}},
            subTaskDetails: {
                node1: {
                    nodeId: 'node1',
                    nodeName: 'test-node-1',
                    taskType: 'PUBSUB',
                    totalClientCount: 1000,
                    taskWorkStage: 'ONGOING'
                },
            },
        })
    })

    it('render_visible_showsTwoTabs', async () => {
        render(
            <TaskDetailModal
                visible={true}

                taskId="task1234"
                onClose={vi.fn()}
            />
        )

        await waitFor(() => {
            expect(screen.getByText('Task Config')).toBeInTheDocument()
            expect(screen.getByText('Subtasks')).toBeInTheDocument()
        })
        // Statistics Tab removed
        expect(screen.queryByText('Statistics')).not.toBeInTheDocument()
    })

    it('render_subTaskTab_showsNodeName', async () => {
        render(
            <TaskDetailModal
                visible={true}

                taskId="task1234"
                onClose={vi.fn()}
            />
        )

        // Subtask Tab content not visible by default, but Tab button exists
        await waitFor(() => {
            expect(screen.getByText('Subtasks')).toBeInTheDocument()
        })

        // Verify basic info loaded (on-demand: only calls basic info API)
        expect(mockLoadTaskBasicInfo).toHaveBeenCalledWith('task1234')
        // Stats and subtask APIs only load when clicking the corresponding Tab
        expect(mockLoadTaskStatistics).not.toHaveBeenCalled()
        expect(mockLoadTaskSubTasks).not.toHaveBeenCalled()
    })

    it('render_statisticsTab_removed', async () => {
        render(
            <TaskDetailModal
                visible={true}

                taskId="task1234"
                onClose={vi.fn()}
            />
        )

        await waitFor(() => {
            expect(screen.getByText('Task Config')).toBeInTheDocument()
        })

        // Statistics Tab removed, should not exist
        expect(screen.queryByText('Statistics')).not.toBeInTheDocument()
        expect(screen.queryByText('Allocation Overview')).not.toBeInTheDocument()
    })

    it('render_noReportModal_noDetailButton', async () => {
        render(
            <TaskDetailModal
                visible={true}

                taskId="task1234"
                onClose={vi.fn()}
            />
        )

        await waitFor(() => {
            expect(screen.getByText('Subtasks')).toBeInTheDocument()
        })

        // Old "Details" link button should not appear
        expect(screen.queryByRole('button', {name: /Details/})).not.toBeInTheDocument()
    })

    it('render_subTaskTab_hasMetricsButton', async () => {
        render(
            <TaskDetailModal
                visible={true}

                taskId="task1234"
                onClose={vi.fn()}
            />
        )

        await waitFor(() => {
            expect(screen.getByText('Subtasks')).toBeInTheDocument()
        })

        // Click subtask Tab
        const subtaskTab = screen.getByRole('tab', {name: 'Subtasks'})
        subtaskTab.click()

        await waitFor(() => {
            expect(screen.getByText('Monitor')).toBeInTheDocument()
        })
    })

    it('render_taskConfig_showsBrokerGroupName', async () => {
        mockGroupApi.getAllGroupsForSelect.mockResolvedValue([
            {id: 'broker-group-1', name: 'Production Brokers'},
        ])
        mockLoadTaskBasicInfo.mockResolvedValue({
            taskId: 'task1234',
            taskName: 'Test Task',
            group: 'group1',
            mainTaskView: {
                taskType: 'PUBSUB',
                template: 'PUBSUB_STANDARD',
                totalClientCount: 1000,
                stressDurationInSec: 60,
                taskWorkStage: 'ONGOING',
            },
            brokers: [{host: 'localhost', port: 1883, group: 'broker-group-1'}],
        })

        render(
            <TaskDetailModal
                visible={true}
                taskId="task1234"
                onClose={vi.fn()}
            />
        )

        await waitFor(() => {
            expect(screen.getByText('Production Brokers')).toBeInTheDocument()
        })
        expect(screen.queryByText('broker-group-1')).not.toBeInTheDocument()
    })

    it('render_stressParams_showsGeneratedPubTopicPattern', async () => {
        mockLoadTaskConfig.mockResolvedValue({
            ...mockTaskConfig,
            taskId: 'task1234',
            topic: undefined,
            payloadMode: 'BIFRO',
        })

        render(
            <TaskDetailModal
                visible={true}
                taskId="task1234"
                onClose={vi.fn()}
            />
        )

        await waitFor(() => {
            expect(screen.getByRole('tab', {name: 'Stress Params'})).toBeInTheDocument()
        })

        screen.getByRole('tab', {name: 'Stress Params'}).click()

        await waitFor(() => {
            expect(screen.getAllByText('task1234/{globalClientIndex}').length).toBeGreaterThan(0)
        })
    })

    it('render_stressParams_withTopicsPerClient_showsTopicOffsetPattern', async () => {
        mockLoadTaskConfig.mockResolvedValue({
            ...mockTaskConfig,
            taskId: 'task1234',
            topic: undefined,
            topicsPerClient: 3,
            payloadMode: 'BIFRO',
        })

        render(
            <TaskDetailModal
                visible={true}
                taskId="task1234"
                onClose={vi.fn()}
            />
        )

        await waitFor(() => {
            expect(screen.getByRole('tab', {name: 'Stress Params'})).toBeInTheDocument()
        })

        screen.getByRole('tab', {name: 'Stress Params'}).click()

        await waitFor(() => {
            expect(screen.getAllByText('task1234/{globalClientIndex}/{topicOffset}').length).toBeGreaterThan(0)
            expect(screen.getAllByText('topicOffset = 0..2').length).toBeGreaterThan(0)
        })
    })

    it('render_dynamicPublishProfile_showsProfileName', async () => {
        mockLoadTaskBasicInfo.mockResolvedValue({
            taskId: 'task1234',
            taskName: 'Test Task',
            group: 'group1',
            mainTaskView: {
                taskType: 'PUBSUB',
                template: 'PUBSUB_STANDARD',
                totalClientCount: 1000,
                stressDurationInSec: 60,
                taskWorkStage: 'ONGOING',
            },
            publishProfile: {
                id: 'profile-1',
                name: 'Prod Morning Wave',
                totalDurationMs: 60000,
                maxQps: 100,
            },
            brokers: [{host: 'localhost', port: 1883}],
        })
        mockLoadTaskConfig.mockResolvedValue({
            ...mockTaskConfig,
            qpsMode: 'DYNAMIC',
            profileConfig: {
                profileId: 'profile-1',
            },
        })

        render(
            <TaskDetailModal
                visible={true}
                taskId="task1234"
                onClose={vi.fn()}
            />
        )

        await waitFor(() => {
            expect(screen.getByRole('tab', {name: 'Stress Params'})).toBeInTheDocument()
        })

        screen.getByRole('tab', {name: 'Stress Params'}).click()

        await waitFor(() => {
            expect(screen.getByText('Prod Morning Wave')).toBeInTheDocument()
        })
        expect(screen.queryByText('profile-1')).not.toBeInTheDocument()
    })

    it('render_dynamicTrafficProfiles_showsProfileNames', async () => {
        mockListProfiles.mockResolvedValue([
            {id: 'connect-profile-id', name: 'Ramp Connect Wave', totalDurationMs: 60000, maxQps: 100},
            {id: 'disconnect-profile-id', name: 'Drain Disconnect Wave', totalDurationMs: 60000, maxQps: 100},
            {id: 'subscribe-profile-id', name: 'Subscribe Rollout Wave', totalDurationMs: 60000, maxQps: 100},
        ])
        mockLoadTaskConfig.mockResolvedValue({
            ...mockTaskConfig,
            connectProfileId: 'connect-profile-id',
            disconnectProfileId: 'disconnect-profile-id',
            subscribeQpsMode: 'DYNAMIC',
            subscribeProfileId: 'subscribe-profile-id',
        })

        render(
            <TaskDetailModal
                visible={true}
                taskId="task1234"
                onClose={vi.fn()}
            />
        )

        await waitFor(() => {
            expect(screen.getByRole('tab', {name: 'Stress Params'})).toBeInTheDocument()
        })

        screen.getByRole('tab', {name: 'Stress Params'}).click()

        await waitFor(() => {
            expect(screen.getByText('Ramp Connect Wave')).toBeInTheDocument()
            expect(screen.getByText('Drain Disconnect Wave')).toBeInTheDocument()
            expect(screen.getByText('Subscribe Rollout Wave')).toBeInTheDocument()
        })
        expect(screen.queryByText('connect-profile-id')).not.toBeInTheDocument()
        expect(screen.queryByText('disconnect-profile-id')).not.toBeInTheDocument()
        expect(screen.queryByText('subscribe-profile-id')).not.toBeInTheDocument()
    })

    it('render_reopenSameTask_usesCacheWithoutClearingConfig', async () => {
        const {rerender} = render(
            <TaskDetailModal
                visible={true}
                taskId="task1234"
                onClose={vi.fn()}
            />
        )

        await waitFor(() => {
            expect(screen.getAllByText('localhost:1883').length).toBeGreaterThan(0)
        })

        rerender(
            <TaskDetailModal
                visible={false}
                taskId={null}
                onClose={vi.fn()}
            />
        )

        rerender(
            <TaskDetailModal
                visible={true}
                taskId="task1234"
                onClose={vi.fn()}
            />
        )

        await waitFor(() => {
            expect(screen.getAllByText('localhost:1883').length).toBeGreaterThan(0)
        })
    })
})
