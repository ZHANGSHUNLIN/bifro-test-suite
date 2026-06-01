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
import userEvent from '@testing-library/user-event'
import NodeMetricsPanel from '../NodeMetricsPanel'
import {nodeApi} from '../../features/node'

// Mock nodeApi
vi.mock('../../features/node', () => ({
    nodeApi: {
        getNodeMetrics: vi.fn(),
    },
}))

const mockedGetNodeMetrics = vi.mocked(nodeApi.getNodeMetrics)

const mockSuccessResponse = {
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
            mean: 0.004,
            p50: 0.004,
            p95: 25.0,
            p99: 45.0,
            max: 120.0,
            totalTime: 1250.0,
            hasData: true,
        },
    ],
}

const mockOfflineResponse = {
    nodeId: 'node1',
    success: false,
    errorCode: 'NODE_OFFLINE',
    errorMessage: 'Node not found or offline',
    timestamp: Date.now(),
    counterMetrics: [],
    timerMetrics: [],
}

const mockTimeoutResponse = {
    nodeId: 'node1',
    success: false,
    errorCode: 'QUERY_TIMEOUT',
    errorMessage: 'Node query timeout or unreachable',
    timestamp: Date.now(),
    counterMetrics: [],
    timerMetrics: [],
}

const mockEmptyResponse = {
    nodeId: 'node1',
    success: true,
    timestamp: Date.now(),
    counterMetrics: [],
    timerMetrics: [],
}

describe('NodeMetricsPanel | component tests', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('render_withSuccessData_showsReportStyleMetricSections', async () => {
        mockedGetNodeMetrics.mockResolvedValue(mockSuccessResponse as never)
        render(<NodeMetricsPanel nodeId="node1" taskId="task1234" nodeName="test-node" onBack={vi.fn()}/>)

        await waitFor(() => {
            expect(screen.getByText('Node Metrics')).toBeInTheDocument()
            expect(screen.getByText('Metrics Snapshot')).toBeInTheDocument()
            expect(screen.getAllByText('Counter').length).toBeGreaterThan(0)
            expect(screen.getAllByText('Timer').length).toBeGreaterThan(0)
        })
    })

    it('render_withTimerData_usesAdaptiveDurationUnits', async () => {
        mockedGetNodeMetrics.mockResolvedValue(mockSuccessResponse as never)
        render(<NodeMetricsPanel nodeId="node1" taskId="task1234" nodeName="test-node" onBack={vi.fn()}/>)

        await waitFor(() => {
            expect(screen.getAllByText('4,000 ns').length).toBeGreaterThan(0)
            expect(screen.getByText('25.00 ms')).toBeInTheDocument()
            expect(screen.getAllByText('120.00 ms').length).toBeGreaterThan(0)
            expect(screen.queryByText('0.00')).not.toBeInTheDocument()
        })
    })

    it('render_showsNodeInfo', async () => {
        mockedGetNodeMetrics.mockResolvedValue(mockSuccessResponse as never)
        render(<NodeMetricsPanel nodeId="node1" taskId="task1234" nodeName="test-node" onBack={vi.fn()}/>)

        await waitFor(() => {
            expect(screen.getByText('node1')).toBeInTheDocument()
            expect(screen.getByText('test-node')).toBeInTheDocument()
        })
    })

    it('render_withOfflineNode_showsAlert', async () => {
        mockedGetNodeMetrics.mockResolvedValue(mockOfflineResponse as never)
        render(<NodeMetricsPanel nodeId="node1" taskId="task1234" nodeName="test-node" onBack={vi.fn()}/>)

        await waitFor(() => {
            expect(screen.getByText('Node is offline, cannot fetch metrics')).toBeInTheDocument()
        })
    })

    it('render_withTimeout_showsAlert', async () => {
        mockedGetNodeMetrics.mockResolvedValue(mockTimeoutResponse as never)
        render(<NodeMetricsPanel nodeId="node1" taskId="task1234" nodeName="test-node" onBack={vi.fn()}/>)

        await waitFor(() => {
            expect(screen.getByText('Query timed out, please retry')).toBeInTheDocument()
        })
    })

    it('render_withEmptyData_showsEmpty', async () => {
        mockedGetNodeMetrics.mockResolvedValue(mockEmptyResponse as never)
        render(<NodeMetricsPanel nodeId="node1" taskId="task1234" nodeName="test-node" onBack={vi.fn()}/>)

        await waitFor(() => {
            expect(screen.getByText('No metrics yet, task may not have produced data')).toBeInTheDocument()
        })
    })

    it('click_backButton_callsOnBack', async () => {
        const user = userEvent.setup()
        const onBack = vi.fn()
        mockedGetNodeMetrics.mockResolvedValue(mockSuccessResponse as never)

        render(<NodeMetricsPanel nodeId="node1" taskId="task1234" nodeName="test-node" onBack={onBack}/>)

        await waitFor(() => {
            expect(screen.getByText('Back to list')).toBeInTheDocument()
        })

        await user.click(screen.getByText('Back to list'))
        expect(onBack).toHaveBeenCalledOnce()
    })

    it('render_showsAutoRefreshSwitch', async () => {
        mockedGetNodeMetrics.mockResolvedValue(mockSuccessResponse as never)
        render(<NodeMetricsPanel nodeId="node1" taskId="task1234" nodeName="test-node" onBack={vi.fn()}/>)

        await waitFor(() => {
            expect(screen.getByText('Auto')).toBeInTheDocument()
        })
    })

    it('render_withSuccessData_showsStrippedMetricLabels', async () => {
        mockedGetNodeMetrics.mockResolvedValue(mockSuccessResponse as never)
        render(<NodeMetricsPanel nodeId="node1" taskId="task1234" nodeName="test-node" onBack={vi.fn()}/>)

        await waitFor(() => {
            // Strip-prefix approach: display string with bifro_task_metric_ prefix removed
            expect(screen.getByText('connect_success_count')).toBeInTheDocument()
            expect(screen.getByText('connect_exception_count')).toBeInTheDocument()
        })
    })
})
