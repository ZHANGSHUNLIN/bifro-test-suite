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
import type {TaskGroup} from '../../../features/group'
import type {PageInfo} from '../../../features/task'

export const mockGroup: TaskGroup = {
    id: 'group1',
    name: 'Test Group',
    description: 'Test group description',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T01:00:00Z',
}

export const mockGroupListResponse: PageInfo<TaskGroup> = {
    content: [mockGroup],
    totalElements: 1,
    totalPages: 1,
    size: 20,
    number: 0,
    numberOfElements: 1,
    first: true,
    last: true,
}

export default [
    // GET /groups/list - get group list
    http.get('/api/groups/list', async () => {
        await delay(100)

        return HttpResponse.json({
            code: 200,
            data: mockGroupListResponse,
        })
    }),

    // GET /groups/all - get all groups (for dropdown)
    http.get('/api/groups/all', async () => {
        await delay(50)
        return HttpResponse.json({
            code: 200,
            data: [mockGroup],
        })
    }),

    // POST /groups - Add Group
    http.post('/api/groups', async ({request}) => {
        const body = await request.json() as Record<string, unknown>
        await delay(100)

        if (!body.name) {
            return HttpResponse.json({code: 400, message: 'name is required'}, {status: 400})
        }

        const newGroup: TaskGroup = {
            ...mockGroup,
            id: 'new' + Math.random().toString(36).substring(7),
            name: body.name as string,
            description: body.description as string,
        }

        return HttpResponse.json({
            code: 200,
            data: newGroup,
        }, {status: 201})
    }),

    // PUT /groups/:id - update group
    http.put('/api/groups/:id', async ({params, request}) => {
        const {id} = params
        const body = await request.json() as Record<string, unknown>
        await delay(100)

        if (id === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Group not found'}, {status: 404})
        }

        const updatedGroup: TaskGroup = {
            ...mockGroup,
            id: id as string,
            name: body.name as string,
            description: body.description as string,
        }

        return HttpResponse.json({
            code: 200,
            data: updatedGroup,
        })
    }),

    // DELETE /groups/:id - delete group
    http.delete('/api/groups/:id', async ({params}) => {
        const {id} = params
        await delay(100)

        if (id === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Group not found'}, {status: 404})
        }

        if (id === 'has-references') {
            return HttpResponse.json({code: 400, message: 'Group is in use'}, {status: 400})
        }

        return HttpResponse.json({
            code: 200,
            data: {success: true},
        })
    }),
] as const
