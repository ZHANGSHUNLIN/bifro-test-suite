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

import {TaskTemplateValues, TaskTypeValues} from '../../features/task';
import i18n from '../../i18n';

// Task form field config interface
export interface FormFieldConfig {
    name: string;
    label: string;
    type: 'input' | 'inputNumber' | 'select' | 'switch' | 'textarea' | 'datePicker' | 'password';
    required?: boolean;
    placeholder?: string;
    initialValue?: any;
    rules?: any[];
    options?: Array<{ label: string; value: string | number }>;
    width?: number | string;
    min?: number;
    max?: number;
    step?: number;
}

// Basic config fields
export const basicFields: FormFieldConfig[] = [
    {
        name: 'taskName',
        label: i18n.t('task.form.taskName'),
        type: 'input',
        placeholder: i18n.t('task.form.taskNamePlaceholder'),
    },
    {
        name: 'taskType',
        label: i18n.t('task.form.taskType'),
        type: 'select',
        required: true,
        initialValue: TaskTypeValues.CONN,
        options: [
            {label: i18n.t('task.type.CONN'), value: TaskTypeValues.CONN},
            {label: i18n.t('task.type.PUBSUB'), value: TaskTypeValues.PUBSUB},
        ],
    },
    {
        name: 'template',
        label: i18n.t('task.form.taskTemplate'),
        type: 'select',
        required: true,
        initialValue: TaskTemplateValues.CONN_STANDARD,
        options: [],
    },
    {
        name: 'protocol',
        label: i18n.t('task.form.protocolType'),
        type: 'select',
        required: true,
        initialValue: 'tcp',
        options: [
            {label: 'TCP', value: 'tcp'},
            // { label: 'SSL', value: 'ssl' },
            // { label: 'WebSocket', value: 'ws' },
            // { label: 'WebSocket Secure', value: 'wss' },
        ],
    },
    {
        name: 'totalClientCount',
        label: i18n.t('task.form.clientCount'),
        type: 'inputNumber',
        required: true,
        initialValue: 100,
        min: 1,
        max: 10000000000,
    },
    {
        name: 'connectRate',
        label: i18n.t('task.form.connectRatePerSec'),
        type: 'inputNumber',
        initialValue: 100,
        min: 1,
        max: 10000000,
        step: 0.1,
    },
    {
        name: 'disconnectRate',
        label: i18n.t('task.form.disconnectRatePerSec'),
        type: 'inputNumber',
        initialValue: 2000,
        min: 0.1,
        max: 10000000,
        step: 0.1,
    },
    {
        name: 'fanOut',
        label: i18n.t('task.form.fanOut'),
        type: 'inputNumber',
        initialValue: 1,
        min: 1,
        max: 10000000000,
    },
    {
        name: 'fanIn',
        label: i18n.t('task.form.fanIn'),
        type: 'inputNumber',
        initialValue: 1,
        min: 1,
        max: 10000000000,
    },
];

// Connection config fields
export const connectionFields: FormFieldConfig[] = [
    {
        name: 'keepAliveInSec',
        label: i18n.t('task.form.keepAlive'),
        type: 'inputNumber',
        initialValue: 120,
        min: 0,
        max: 3600,
    },
    {
        name: 'stageTimeoutInSec',
        label: i18n.t('task.form.stageTimeout'),
        type: 'inputNumber',
        initialValue: 30,
        min: 1,
        max: 3600,
    },
    {
        name: 'delayAfterStageInSec',
        label: i18n.t('task.form.delayAfterStage'),
        type: 'inputNumber',
        initialValue: 30,
        min: 0,
        max: 300,
    },
    {
        name: 'expiryIntervalInSec',
        label: i18n.t('task.form.expiryInterval'),
        type: 'inputNumber',
        initialValue: 120,
        min: 0,
        max: 86400,
    },
    {
        name: 'stressDurationInSec',
        label: i18n.t('task.form.stressDuration'),
        type: 'inputNumber',
        initialValue: 60,
        min: 1,
        max: 1000000000,
    },
];

// Auth config fields
export const authFields: FormFieldConfig[] = [];

// Protocol config fields
export const protocolFields: FormFieldConfig[] = [
    {
        name: 'cleanSession',
        label: i18n.t('task.form.cleanSession'),
        type: 'switch',
        initialValue: true,
    },
    {
        name: 'mqtt5',
        label: 'MQTT 5.0',
        type: 'switch',
        initialValue: false,
    },
    {
        name: 'autoMultiAddress',
        label: i18n.t('task.form.autoMultiAddress'),
        type: 'switch',
        initialValue: true,
    },
];

// Client ID config fields
export const clientIdFields: FormFieldConfig[] = [
    {
        name: 'emptyClientId',
        label: i18n.t('task.form.emptyClientId'),
        type: 'switch',
        initialValue: false,
    },
];

// Connection timeout config fields
export const timeoutFields: FormFieldConfig[] = [
    {
        name: 'connectTimeoutInMs',
        label: i18n.t('task.form.connectTimeout'),
        type: 'inputNumber',
        initialValue: 10000,
        min: 100,
        max: 60000,
    },
    {
        name: 'ackTimeoutInSec',
        label: i18n.t('task.form.ackTimeout'),
        type: 'inputNumber',
        initialValue: 120,
        min: 1,
        max: 3600,
    },
    {
        name: 'reconnectMaxAttempts',
        label: i18n.t('task.form.reconnectMaxAttempts'),
        type: 'inputNumber',
        initialValue: 2,
        min: 1,
        max: 100,
    },
    {
        name: 'reconnectIntervalInMs',
        label: i18n.t('task.form.reconnectInterval'),
        type: 'inputNumber',
        initialValue: 5000,
        min: 100,
        max: 30000,
    },
    {
        name: 'maxInflightQueue',
        label: i18n.t('task.form.maxQueueSize'),
        type: 'inputNumber',
        initialValue: 200,
        min: 10,
        max: 10000,
    },

];

// Will config fields
export const willFields: FormFieldConfig[] = [
    {
        name: 'willConfig.willFlag',
        label: i18n.t('task.form.enableWill'),
        type: 'switch',
        initialValue: false,
    },
    {
        name: 'willConfig.willTopic',
        label: i18n.t('task.form.willTopic'),
        type: 'input',
        placeholder: i18n.t('task.form.willTopicPlaceholder'),
        initialValue: 'last/{{client_id_short}}',
    },
    {
        name: 'willConfig.willMessage',
        label: i18n.t('task.form.willMessage'),
        type: 'input',
        placeholder: i18n.t('task.form.willMessagePlaceholder'),
        initialValue: 'last xxxxx',
    },
    {
        name: 'willConfig.willMessageLen',
        label: i18n.t('task.form.willMessageLen'),
        type: 'inputNumber',
        placeholder: i18n.t('task.form.willMessageLenPlaceholder'),
        initialValue: 10,
        min: 0,
        max: 65535,
    },
    {
        name: 'willConfig.willQos',
        label: i18n.t('task.form.willQos'),
        type: 'select',
        initialValue: 1,
        options: [
            {label: 'QoS 0', value: 0},
            {label: 'QoS 1', value: 1},
            {label: 'QoS 2', value: 2},
        ],
    },
    {
        name: 'willConfig.willRetain',
        label: i18n.t('task.form.willRetain'),
        type: 'switch',
        initialValue: false,
    },
];

// All field mappings
export const formFieldGroups = {
    basic: basicFields,
    connection: connectionFields,
    auth: authFields,
    protocol: protocolFields,
    clientId: clientIdFields,
    timeout: timeoutFields,
    will: willFields,
};
