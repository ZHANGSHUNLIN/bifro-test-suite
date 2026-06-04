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

import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {describe, expect, it, vi} from 'vitest';
import type {SubTaskDetail} from '../../../../../features/task';
import TaskSubTasksSection from '../TaskSubTasksSection';

const completedSubTaskDetails: Record<string, SubTaskDetail> = {
    'worker-1': {
        nodeId: 'worker-1',
        nodeName: 'worker-1',
        taskType: 'CONN',
        totalClientCount: 100,
        taskWorkStage: 'SHUTDOWN',
        counterMetrics: [
            {
                name: 'bifro_task_metric_connect_success_count',
                tags: {taskId: 'task-1', nodeId: 'worker-1'},
                count: 100,
            },
        ],
        timerMetrics: [
            {
                name: 'bifro_task_metric_connect_latency',
                tags: {taskId: 'task-1', nodeId: 'worker-1'},
                count: 100,
                mean: 1.5,
                p50: 1,
                p95: 2,
                p99: 3,
                max: 4,
                totalTime: 150,
                hasData: true,
            },
        ],
    },
};

describe('TaskSubTasksSection | component tests', () => {
    it('clickMonitor_withCompletedSnapshotMetrics_showsSnapshotMetricsWithoutLiveQuery', async () => {
        const user = userEvent.setup();
        const onShowMetrics = vi.fn();

        render(
            <TaskSubTasksSection
                subTaskDetails={completedSubTaskDetails}
                onShowMetrics={onShowMetrics}
                onShowInstances={vi.fn()}
            />
        );

        await user.click(screen.getByRole('button', {name: /Monitor/i}));

        expect(onShowMetrics).not.toHaveBeenCalled();
        await waitFor(() => {
            expect(screen.getByText('Node Metrics Detail')).toBeInTheDocument();
            expect(screen.getByText('connect_success_count')).toBeInTheDocument();
            expect(screen.getByText('connect_latency')).toBeInTheDocument();
        });
    });
});
