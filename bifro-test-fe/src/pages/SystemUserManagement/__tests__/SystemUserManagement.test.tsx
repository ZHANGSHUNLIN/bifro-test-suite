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
import SystemUserManagement from '../index';
import systemUserApi from '../../../features/systemUser';

vi.mock('../../../features/systemUser', () => ({
    default: {
        list: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
        delete: vi.fn(),
        resetPassword: vi.fn(),
    },
}));

const mockedSystemUserApi = vi.mocked(systemUserApi);

describe('SystemUserManagement | pagination', () => {
    it('loads requested page when table page changes', async () => {
        const user = userEvent.setup();
        mockedSystemUserApi.list.mockImplementation(async (pageNum = 1, pageSize = 20) => ({
            content: [
                {
                    id: `user-${pageNum}`,
                    username: `user${pageNum}`,
                    roles: ['VIEWER'],
                    enabled: true,
                    createdAt: '2024-01-01T00:00:00Z',
                    updatedAt: '2024-01-01T00:00:00Z',
                },
            ],
            totalElements: 40,
            totalPages: 2,
            size: pageSize,
            number: pageNum - 1,
            numberOfElements: 1,
            first: pageNum === 1,
            last: pageNum === 2,
        }));

        render(<SystemUserManagement/>);

        await waitFor(() => {
            expect(mockedSystemUserApi.list).toHaveBeenCalledWith(1, 20);
        });

        await user.click(screen.getByTitle('2'));

        await waitFor(() => {
            expect(mockedSystemUserApi.list).toHaveBeenLastCalledWith(2, 20);
        });
        expect(screen.getByText('user2')).toBeInTheDocument();
        expect(mockedSystemUserApi.list).toHaveBeenCalledTimes(2);
    });
});
