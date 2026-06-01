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

import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {api, AUTH_EXPIRED_EVENT} from '../request';

describe('request utility', () => {
    beforeEach(() => {
        vi.useRealTimers();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    it('replaces path params without appending them as query params', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({code: 200, data: {ok: true}}),
        } as Response);

        await api.get('/task/:id/basic', {params: {id: 'task 1'}});

        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8090/api/task/task%201/basic');
    });

    it('keeps non-path params in the query string', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({code: 200, data: {ok: true}}),
        } as Response);

        await api.get('/groups/:id', {params: {id: 'group1', type: 'BROKER'}});

        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8090/api/groups/group1?type=BROKER');
    });

    it('aborts requests that do not complete before the default timeout', async () => {
        vi.useFakeTimers();
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((_url, init) => new Promise((_resolve, reject) => {
            const signal = init?.signal;
            signal?.addEventListener('abort', () => reject(signal.reason), {once: true});
        }));

        const requestPromise = api.get('/node/allNodes');
        const expectation = expect(requestPromise).rejects.toThrow('Request timed out');
        await vi.advanceTimersByTimeAsync(15000);

        await expectation;
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('dispatches auth expired event when response is unauthorized', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue({
            ok: false,
            status: 401,
        } as Response);
        const authExpiredHandler = vi.fn();
        window.addEventListener(AUTH_EXPIRED_EVENT, authExpiredHandler);

        await expect(api.get('/task/list')).rejects.toThrow('HTTP error! status: 401');

        expect(authExpiredHandler).toHaveBeenCalledTimes(1);
        window.removeEventListener(AUTH_EXPIRED_EVENT, authExpiredHandler);
    });
});
