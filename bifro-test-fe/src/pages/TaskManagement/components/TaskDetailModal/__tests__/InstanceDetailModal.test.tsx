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
import {beforeEach, describe, expect, it, vi} from 'vitest';
import InstanceDetailModal from '../InstanceDetailModal';

const {mockGetClientInstances} = vi.hoisted(() => ({
    mockGetClientInstances: vi.fn(),
}));

vi.mock('../../../../../features/node', () => ({
    default: {
        getClientInstances: mockGetClientInstances,
    },
}));

describe('InstanceDetailModal', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockGetClientInstances.mockResolvedValue({
            success: true,
            clients: [
                {
                    clientId: 'client-1',
                    host: 'broker.example.com',
                    port: 1883,
                    localAddress: '10.0.0.8',
                    localPort: 10000,
                    status: 'CONNECTED',
                    connectedAt: 1710000000000,
                    clientType: 'conn',
                },
                {
                    clientId: 'client-2',
                    host: 'broker.example.com',
                    port: 1883,
                    localAddress: '10.0.0.8',
                    status: 'CONNECTED',
                    connectedAt: 1710000000000,
                    clientType: 'conn',
                },
            ],
            total: 2,
            page: 0,
            size: 20,
            totalPages: 1,
        });
    });

    it('render_localEndpointColumn_showsAddressPortOrDash', async () => {
        render(
            <InstanceDetailModal
                visible={true}
                nodeId="node-1"
                taskId="task-1"
                nodeName="node-a"
                onClose={vi.fn()}
            />
        );

        await waitFor(() => {
            expect(screen.getByText('10.0.0.8:10000')).toBeInTheDocument();
        });
        expect(screen.getByText('10.0.0.8:-')).toBeInTheDocument();
    });
});
