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

import {describe, expect, it, vi} from 'vitest';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AuditLogs from '../index';
import auditApi from '../../../features/audit';

vi.mock('../../../features/audit', () => ({
    default: {
        list: vi.fn(),
    },
}));

const mockedAuditApi = vi.mocked(auditApi);

describe('AuditLogs | pagination', () => {
    it('keeps requested page after page change', async () => {
        const user = userEvent.setup();
        mockedAuditApi.list.mockImplementation(async query => ({
            content: [
                {
                    id: `log-${query.pageNum}`,
                    username: 'admin',
                    action: 'TASK_CREATE',
                    resourceType: 'TASK',
                    resourceId: `task-${query.pageNum}`,
                    success: true,
                    message: `page ${query.pageNum}`,
                    createdAt: '2024-01-01T00:00:00Z',
                },
            ],
            totalElements: 40,
            totalPages: 2,
            size: query.pageSize ?? 20,
            number: (query.pageNum ?? 1) - 1,
            numberOfElements: 1,
            first: query.pageNum === 1,
            last: query.pageNum === 2,
        }));

        render(<AuditLogs/>);

        await waitFor(() => {
            expect(mockedAuditApi.list).toHaveBeenCalledWith(expect.objectContaining({pageNum: 1, pageSize: 20}));
        });

        await user.click(screen.getByTitle('2'));

        await waitFor(() => {
            expect(mockedAuditApi.list).toHaveBeenLastCalledWith(expect.objectContaining({pageNum: 2, pageSize: 20}));
        });
        expect(screen.getByText('page 2')).toBeInTheDocument();
        expect(mockedAuditApi.list).toHaveBeenCalledTimes(2);
    });
});
