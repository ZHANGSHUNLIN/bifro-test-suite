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

import {render, screen, waitFor} from '@testing-library/react'
import {http, HttpResponse} from 'msw'
import {describe, expect, it} from 'vitest'
import TaskStateFlowSection from '../TaskStateFlowSection'
import {server} from '../../../../../test/setup'
import type {TaskDetailResponse} from '../../../../../features/task'

const taskDetail: TaskDetailResponse = {
    success: true,
    taskId: 'task1234',
    brokers: [],
    mainTaskView: {
        taskId: 'task1234',
        taskType: 'PUBSUB',
        template: 'PUBSUB_STANDARD',
        totalClientCount: 1000,
        stressDurationInSec: 60,
        taskWorkStage: 'STARTING',
    },
    subTaskDetails: {
        node1: {
            nodeId: 'node1',
            nodeName: 'test-node-1',
            taskType: 'PUBSUB',
            totalClientCount: 1000,
            taskWorkStage: 'STARTING',
            pipelineStages: [
                {
                    key: 'InitPubClients',
                    label: 'Init Publishers',
                    visible: true,
                    status: 'RUNNING',
                    startedAt: 1780040944096,
                },
            ],
        },
    },
}

describe('TaskStateFlowSection | component tests', () => {
    it('render_stateTimeline_filtersNonTransitionHistoryRows', async () => {
        server.use(
            http.get('http://localhost:8090/api/task/task1234/state-history', () => HttpResponse.json({
                code: 200,
                data: [
                    {
                        timestamp: 1780040939000,
                        nodeId: 'node1',
                        errorMessage: 'Local source port conflicts detected and excluded from allocation.',
                        source: 'ASSIGNMENT_PREFLIGHT',
                    },
                    {
                        fromStage: 'ASSIGNED',
                        toStage: 'STARTING',
                        triggerEvent: 'START_TASK',
                        timestamp: 1780040944093,
                        nodeId: 'node1',
                        source: 'SUB_TASK',
                    },
                ],
            }))
        )

        render(<TaskStateFlowSection taskDetail={taskDetail}/>)

        await waitFor(() => {
            expect(screen.getByText('START_TASK')).toBeInTheDocument()
        })

        expect(screen.getByText((_content, element) =>
            element?.textContent === 'Assigned → StartingSTART_TASK'
        )).toBeInTheDocument()
        expect(screen.queryByText('Local source port conflicts detected and excluded from allocation.')).not.toBeInTheDocument()
    })
})
