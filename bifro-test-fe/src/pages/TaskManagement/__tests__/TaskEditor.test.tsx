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
import {http, HttpResponse} from 'msw';
import {MemoryRouter} from 'react-router-dom';
import TaskEditor from '../TaskEditor';
import {server} from '../../../test/setup';
import {TaskStatusValues, TaskTemplateValues, TaskTypeValues, type TaskListItem} from '../../../features/task';

const apiBase = 'http://localhost:8090/api';

describe('TaskEditor | copy mode', () => {
    const mockEditorLookups = () => {
        server.use(
            http.get(`${apiBase}/groups/all`, ({request}) => {
                const url = new URL(request.url);
                const type = url.searchParams.get('type');
                return HttpResponse.json({
                    code: 200,
                    data: type === 'BROKER'
                        ? [{id: 'group1', name: 'Broker Group'}]
                        : [{id: 'taskGroup1', name: 'Task Group'}],
                });
            }),
            http.get(`${apiBase}/task/templates`, () => HttpResponse.json({
                code: 200,
                data: [
                    {value: TaskTemplateValues.CONN_STANDARD, label: 'Connection Standard Template', type: 'CONN'},
                    {value: TaskTemplateValues.PUBSUB_STANDARD, label: 'PubSub Standard Template', type: 'PUBSUB'},
                ],
            })),
            http.get(`${apiBase}/certificates/all`, () => HttpResponse.json({code: 200, data: []})),
            http.get(`${apiBase}/broker/list`, () => HttpResponse.json({
                code: 200,
                data: {
                    content: [{brokerId: 'broker1', name: 'Broker One', host: 'localhost', port: 1883, group: 'group1'}],
                    totalElements: 1,
                    totalPages: 1,
                    size: 20,
                    number: 0,
                    numberOfElements: 1,
                    first: true,
                    last: true,
                },
            })),
            http.get(`${apiBase}/profile`, () => HttpResponse.json({
                code: 200,
                data: {
                    content: [],
                    totalElements: 0,
                    totalPages: 0,
                    size: 20,
                    number: 0,
                    numberOfElements: 0,
                    first: true,
                    last: true,
                },
            })),
        );
    };

    it('submitCopyWithoutOpeningHiddenTabs_preservesStressAndAdvancedValues', async () => {
        const user = userEvent.setup();
        const onOk = vi.fn().mockResolvedValue(undefined);
        const copiedTask: TaskListItem = {
            id: '',
            taskId: '',
            taskName: 'Copied Task',
            taskType: TaskTypeValues.PUBSUB,
            protocol: 'mqtt',
            group: 'group1',
            brokers: [{brokerId: 'broker1', host: 'localhost', port: 1883, group: 'group1'}],
            totalClientCount: 1234,
            status: TaskStatusValues.INIT,
            taskConfig: {
                taskType: TaskTypeValues.PUBSUB,
                template: TaskTemplateValues.PUBSUB_STANDARD,
                protocol: 'mqtt',
                group: 'group1',
                brokers: [{brokerId: 'broker1', host: 'localhost', port: 1883, group: 'group1'}],
                port: 1883,
                totalClientCount: 1234,
                connectRate: 321,
                disconnectRate: 654,
                stressDurationInSec: 789,
                stageTimeoutInSec: 45,
                delayAfterStageInSec: 12,
                messageSize: 2048,
                publishRate: 1.5,
                qos: 1,
                retain: true,
                cleanSession: false,
                keepAliveInSec: 55,
                expiryIntervalInSec: 66,
                maxInflightQueue: 777,
                connectTimeoutInMs: 8888,
                ackTimeoutInSec: 99,
                reconnectMaxAttempts: 8,
                reconnectIntervalInMs: 2222,
                enableAutoMultiAddress: false,
                isMqtt5: true,
                isEmptyClientId: true,
                willConfig: {
                    willFlag: true,
                    willTopic: 'will/topic',
                    willMessage: 'bye',
                    willQos: 1,
                    willRetain: true,
                },
            },
        };

        mockEditorLookups();

        render(
            <MemoryRouter>
                <TaskEditor
                    visible
                    editingTask={copiedTask}
                    onCancel={vi.fn()}
                    onOk={onOk}
                />
            </MemoryRouter>,
        );

        await waitFor(() => expect(screen.getByDisplayValue('Copied Task')).toBeInTheDocument());
        await user.click(screen.getByRole('button', {name: 'OK'}));

        await waitFor(() => expect(onOk).toHaveBeenCalledTimes(1));
        const submitted = onOk.mock.calls[0][1];
        expect(submitted.totalClientCount).toBe(1234);
        expect(submitted.connectRate).toBe(321);
        expect(submitted.disconnectRate).toBe(654);
        expect(submitted.stressDurationInSec).toBe(789);
        expect(submitted.messageSize).toBe(2048);
        expect(submitted.publishRate).toBe(1.5);
        expect(submitted.cleanSession).toBe(false);
        expect(submitted.keepAliveInSec).toBe(55);
        expect(submitted.expiryIntervalInSec).toBe(66);
        expect(submitted.maxInflightQueue).toBe(777);
        expect(submitted.mqtt5).toBe(true);
        expect(submitted.isMqtt5).toBe(true);
        expect(submitted.emptyClientId).toBe(true);
        expect(submitted.isEmptyClientId).toBe(true);
        expect(submitted.willConfig).toMatchObject({
            willFlag: true,
            willTopic: 'will/topic',
            willMessage: 'bye',
            willQos: 1,
            willRetain: true,
        });
    });

    it('copyWithBrokerSnapshotsMissingGroup_fallsBackToTaskGroup', async () => {
        const copiedTask: TaskListItem = {
            id: '',
            taskId: '',
            taskName: 'Copied Task',
            taskType: TaskTypeValues.CONN,
            protocol: 'mqtt',
            group: 'group1',
            brokers: [{brokerId: 'broker1', host: 'localhost', port: 1883}],
            totalClientCount: 100,
            status: TaskStatusValues.INIT,
            taskConfig: {
                taskType: TaskTypeValues.CONN,
                template: TaskTemplateValues.CONN_STANDARD,
                protocol: 'mqtt',
                group: 'group1',
                brokers: [{brokerId: 'broker1', host: 'localhost', port: 1883}],
                port: 1883,
                totalClientCount: 100,
            },
        };

        mockEditorLookups();

        render(
            <MemoryRouter>
                <TaskEditor
                    visible
                    editingTask={copiedTask}
                    onCancel={vi.fn()}
                    onOk={vi.fn()}
                />
            </MemoryRouter>,
        );

        await waitFor(() => expect(screen.getByDisplayValue('Copied Task')).toBeInTheDocument());
        await waitFor(() => expect(screen.getAllByText('Broker Group').length).toBeGreaterThan(0));
    });
});
