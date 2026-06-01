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

import {beforeEach, describe, expect, it, vi} from 'vitest';
import {render, screen, waitFor} from '@testing-library/react';
import NodeMetricsModal from '../NodeMetricsModal';
import {nodeApi} from '../../../../../features/node';

vi.mock('../../../../../features/node', () => ({
    nodeApi: {
        getNodeMetrics: vi.fn(),
    },
}));

const mockedGetNodeMetrics = vi.mocked(nodeApi.getNodeMetrics);

const mockMetricsResponse = {
    nodeId: 'node1',
    success: true,
    timestamp: Date.now(),
    counterMetrics: [],
    timerMetrics: [],
};

describe('NodeMetricsModal', () => {
    const defaultProps = {
        visible: true,
        nodeId: 'node1',
        taskId: 'task123',
        nodeName: 'Node 1',
        isTaskCompleted: false,
        onClose: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
        mockedGetNodeMetrics.mockResolvedValue(mockMetricsResponse as never);
    });

    it('should render Modal title correctly', () => {
        render(<NodeMetricsModal {...defaultProps} />);

        expect(screen.getByText(/node1/i)).toBeInTheDocument();
        expect(screen.getByText(/Node 1/i)).toBeInTheDocument();
    });

    it('should call onClose when close button clicked', async () => {
        render(<NodeMetricsModal {...defaultProps} />);

        // Modal close button (×) triggers onCancel → onClose
        const closeButton = await screen.findByRole('button', {name: /close/i});
        closeButton.click();

        expect(defaultProps.onClose).toHaveBeenCalledTimes(1);
    });

    it('should not show auto-refresh toggle after task completes', async () => {
        render(<NodeMetricsModal {...defaultProps} isTaskCompleted={true}/>);

        await waitFor(() => {
            expect(screen.queryByText('Auto')).not.toBeInTheDocument();
        });
    });

    it('should show auto-refresh toggle when task is not completed', async () => {
        render(<NodeMetricsModal {...defaultProps} isTaskCompleted={false}/>);

        await waitFor(() => {
            expect(screen.getByText(/Auto/i)).toBeInTheDocument();
        });
    });
});
