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
import type {MqttBrokerConfig} from '../../../features/broker'
import type {PageInfo} from '../../../features/task'

export const mockMqttBroker: MqttBrokerConfig = {
    id: 'broker1',
    name: 'Test Broker',
    host: 'localhost',
    port: 1883,
    description: 'Test broker description',
    enabled: true,
    group: 'group1',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T01:00:00Z',
}

export const mockBrokerListResponse: PageInfo<MqttBrokerConfig> = {
    content: [mockMqttBroker],
    totalElements: 1,
    totalPages: 1,
    size: 20,
    number: 0,
    numberOfElements: 1,
    first: true,
    last: true,
}

export default [
    // GET /broker/list - Get Broker List
    http.get('/api/broker/list', async ({request}) => {
        const url = new URL(request.url)
        const group = url.searchParams.get('group')
        await delay(100)

        let content = [...mockBrokerListResponse.content] as MqttBrokerConfig[]
        if (group) {
            content = content.filter((b) => b.group === group)
        }

        return HttpResponse.json({
            code: 200,
            data: {...mockBrokerListResponse, content, numberOfElements: content.length},
        })
    }),

    // GET /broker/:id - get Broker details
    http.get('/api/broker/:id', async ({params}) => {
        const {id} = params
        await delay(50)

        if (id === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Broker not found'}, {status: 404})
        }

        return HttpResponse.json({
            code: 200,
            data: mockMqttBroker,
        })
    }),

    // POST /broker/add - Add Broker
    http.post('/api/broker/add', async ({request}) => {
        const body = await request.json() as Record<string, unknown>
        await delay(100)

        if (!body.name || !body.host || !body.port) {
            return HttpResponse.json({code: 400, message: 'name, host and port are required'}, {status: 400})
        }

        const newBroker: MqttBrokerConfig = {
            ...mockMqttBroker,
            id: 'new' + Math.random().toString(36).substring(7),
            name: body.name as string,
            host: body.host as string,
            port: body.port as number,
            description: body.description as string,
            group: body.group as string,
        }

        return HttpResponse.json({
            code: 200,
            data: newBroker,
        }, {status: 201})
    }),

    // PUT /broker/:id - Update Broker
    http.put('/api/broker/:id', async ({params, request}) => {
        const {id} = params
        const body = await request.json() as Record<string, unknown>
        await delay(100)

        if (id === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Broker not found'}, {status: 404})
        }

        const updatedBroker: MqttBrokerConfig = {
            ...mockMqttBroker,
            id: id as string,
            name: body.name as string,
            host: body.host as string,
            port: body.port as number,
            description: body.description as string,
            group: body.group as string,
        }

        return HttpResponse.json({
            code: 200,
            data: updatedBroker,
        })
    }),

    // DELETE /broker/:id - Delete Broker
    http.delete('/api/broker/:id', async ({params}) => {
        const {id} = params
        await delay(100)

        if (id === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Broker not found'}, {status: 404})
        }

        if (id === 'has-tasks') {
            return HttpResponse.json({code: 400, message: 'Broker is in use'}, {status: 400})
        }

        return HttpResponse.json({
            code: 200,
            data: {success: true},
        })
    }),

    // PATCH /broker/:id/status - toggle Broker status
    http.patch('/api/broker/:id/status', async ({params}) => {
        const {id} = params
        await delay(100)

        if (id === 'not-found') {
            return HttpResponse.json({code: 404, message: 'Broker not found'}, {status: 404})
        }

        const updatedBroker: MqttBrokerConfig = {
            ...mockMqttBroker,
            enabled: !mockMqttBroker.enabled,
        }

        return HttpResponse.json({
            code: 200,
            data: updatedBroker,
        })
    }),
] as const
