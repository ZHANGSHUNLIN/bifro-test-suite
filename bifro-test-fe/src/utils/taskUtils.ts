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

import {TaskStatusValues, TaskTemplateValues, TaskTypeValues} from '../features/task';
import i18n, {getResolvedLocale} from '../i18n';

export const statusConfig: Record<string, { color: string }> = {
    [TaskStatusValues.INIT]: {color: 'default'},
    [TaskStatusValues.ASSIGNED]: {color: 'processing'},
    [TaskStatusValues.STARTING]: {color: 'processing'},
    [TaskStatusValues.ONGOING]: {color: 'processing'},
    [TaskStatusValues.SHUTTING]: {color: 'warning'},
    [TaskStatusValues.SHUTDOWN]: {color: 'success'},
    [TaskStatusValues.STOPPED]: {color: 'default'},
    [TaskStatusValues.FAILED]: {color: 'error'},
    [TaskStatusValues.TIMEOUT]: {color: 'error'},
    // Legacy state compatibility (server may run old version)
    [TaskStatusValues.START]: {color: 'processing'},
    [TaskStatusValues.CONNECTING]: {color: 'processing'},
    [TaskStatusValues.INIT_PUB_CLIENT]: {color: 'processing'},
    [TaskStatusValues.INIT_SUB_CLIENT]: {color: 'processing'},
    [TaskStatusValues.PUB_SUB_CLIENT_READY]: {color: 'processing'},
    [TaskStatusValues.PUB_SUB_CLIENT_START]: {color: 'processing'},
    [TaskStatusValues.PUB_CLIENT_CONN]: {color: 'processing'},
    [TaskStatusValues.SUB_CLIENT_CONN]: {color: 'processing'},
    [TaskStatusValues.SUBSCRIBE_CLIENT]: {color: 'processing'},
    [TaskStatusValues.INIT_KAFKA_CLIENT]: {color: 'processing'},
    [TaskStatusValues.PRODUCING]: {color: 'processing'},
    [TaskStatusValues.DATABASE_CONNECTING]: {color: 'processing'},
    [TaskStatusValues.DATABASE_OPERATING]: {color: 'processing'},
};

export const taskTypeConfig: Record<string, { color: string }> = {
    [TaskTypeValues.CONN]: {color: 'blue'},
    [TaskTypeValues.PUBSUB]: {color: 'green'}
};

export const getStatusText = (status: string): string =>
    i18n.t(`task.status.${status}`, {defaultValue: status});

export const getStatusColor = (status: string): string => {
    return statusConfig[status]?.color || 'default';
};

export const getTaskTypeText = (type: string): string =>
    i18n.t(`task.type.${type}`, {defaultValue: type});

export const getTaskTypeColor = (type: string): string => {
    return taskTypeConfig[type]?.color || 'default';
};

export const runningStatuses = [
    TaskStatusValues.START,
    TaskStatusValues.CONNECTING,
    TaskStatusValues.INIT_PUB_CLIENT,
    TaskStatusValues.INIT_SUB_CLIENT,
    TaskStatusValues.PUB_CLIENT_CONN,
    TaskStatusValues.SUB_CLIENT_CONN,
    TaskStatusValues.SUBSCRIBE_CLIENT,
    TaskStatusValues.PUB_SUB_CLIENT_READY,
    TaskStatusValues.PUB_SUB_CLIENT_START,
    TaskStatusValues.STARTING,
    TaskStatusValues.ONGOING,
    TaskStatusValues.SHUTTING,
];

export const canEditTask = (status: string): boolean => {
    return status === TaskStatusValues.INIT;
};

export const canAssignTask = (status: string): boolean => {
    return status === TaskStatusValues.INIT;
};

export const canConfirmTask = (status: string): boolean => {
    return status === TaskStatusValues.ASSIGNED;
};

export const canStopTask = (status: string): boolean => {
    return isTaskRunning(status) && status !== TaskStatusValues.SHUTTING;
};

export const isTaskRunning = (status: string): boolean => {
    return runningStatuses.includes(status as (typeof runningStatuses)[number]);
};

export const canDeleteTask = (status: string): boolean => {
    return [
        TaskStatusValues.INIT,
        TaskStatusValues.STOPPED,
        TaskStatusValues.SHUTDOWN,
        TaskStatusValues.FAILED,
        TaskStatusValues.TIMEOUT,
    ].includes(status as 'INIT' | 'STOPPED' | 'SHUTDOWN' | 'FAILED' | 'TIMEOUT');
};

export const selectableStatuses = [
    TaskStatusValues.INIT,
    TaskStatusValues.STOPPED,
    TaskStatusValues.SHUTDOWN,
    TaskStatusValues.FAILED,
    TaskStatusValues.TIMEOUT,
];

export const canSelectTask = (status: string): boolean => {
    return selectableStatuses.includes(status as 'INIT' | 'STOPPED' | 'SHUTDOWN' | 'FAILED' | 'TIMEOUT');
};

export const canCopyTask = (_status: string): boolean => {
    return true;
};

export const generateCopyTaskName = (originalName: string, maxLength: number = 100): string => {
    const suffix = i18n.t('task.copyNameSuffix');
    const legacyZhCopySuffix = ` - ${String.fromCharCode(0x526f, 0x672c)}`;
    const suffixes = Array.from(new Set([suffix, ' - Copy', legacyZhCopySuffix]));
    const escapeRegExp = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

    let baseName = originalName;
    let copyNumber = 1;

    for (const copySuffix of suffixes) {
        const copyRegex = new RegExp(`^(.+?)${escapeRegExp(copySuffix)}(?: \\((\\d+)\\))?$`);
        const match = originalName.match(copyRegex);
        if (match) {
            baseName = match[1];
            copyNumber = match[2] ? parseInt(match[2], 10) + 1 : 2;
            break;
        }
    }

    const suffixWithCounter = copyNumber === 1 ? suffix : `${suffix} (${copyNumber})`;
    let copyName = `${baseName}${suffixWithCounter}`;

    if (copyName.length > maxLength) {
        const availableLength = maxLength - suffixWithCounter.length;
        if (availableLength > 0) {
            copyName = `${baseName.substring(0, availableLength)}${suffixWithCounter}`;
        } else {
            copyName = suffixWithCounter.substring(0, maxLength);
        }
    }

    return copyName;
};

export const protocolOptions = [
    {label: 'MQTT 3.1', value: 'MQTT_3_1'},
    {label: 'MQTT 3.1.1', value: 'MQTT_3_1_1'},
    {label: 'MQTT 5.0', value: 'MQTT_5_0'},
];

export const taskTypeOptions = [
    {label: i18n.t('task.typeOptions.conn'), value: TaskTypeValues.CONN},
    {label: i18n.t('task.typeOptions.pubsub'), value: TaskTypeValues.PUBSUB},
];

export const getTaskTypeOptions = () => [
    {label: i18n.t('task.typeOptions.conn'), value: TaskTypeValues.CONN},
    {label: i18n.t('task.typeOptions.pubsub'), value: TaskTypeValues.PUBSUB},
];

// ================== Pipeline Stage Config ==================

export interface PipelineStageConfig {
    key: string;
    label: string;
}

export const CONN_PIPELINE_STAGES: PipelineStageConfig[] = [
    {key: 'INIT_CLIENT', label: i18n.t('task.pipeline.INIT_CLIENT')},
    {key: 'ONGOING', label: i18n.t('task.pipeline.ONGOING')},
    {key: 'SHUTTING', label: i18n.t('task.pipeline.SHUTTING')},
    {key: 'SHUTDOWN', label: i18n.t('task.pipeline.SHUTDOWN')},
];

export const PUBSUB_PIPELINE_STAGES: PipelineStageConfig[] = [
    {key: 'INIT_PUB_CLIENT', label: i18n.t('task.pipeline.INIT_PUB_CLIENT')},
    {key: 'INIT_SUB_CLIENT', label: i18n.t('task.pipeline.INIT_SUB_CLIENT')},
    {key: 'PUB_CLIENT_CONN', label: i18n.t('task.pipeline.PUB_CLIENT_CONN')},
    {key: 'SUB_CLIENT_CONN', label: i18n.t('task.pipeline.SUB_CLIENT_CONN')},
    {key: 'SUBSCRIBE_CLIENT', label: i18n.t('task.pipeline.SUBSCRIBE_CLIENT')},
    {key: 'PUB_SUB_CLIENT_READY', label: i18n.t('task.pipeline.PUB_SUB_CLIENT_READY')},
    {key: 'PUB_SUB_CLIENT_START', label: i18n.t('task.pipeline.PUB_SUB_CLIENT_START')},
    {key: 'ONGOING', label: i18n.t('task.pipeline.ONGOING')},
    {key: 'SHUTTING', label: i18n.t('task.pipeline.SHUTTING')},
    {key: 'SHUTDOWN', label: i18n.t('task.pipeline.SHUTDOWN')},
];

export const PUBSUB_PUB_ONLY_PIPELINE_STAGES: PipelineStageConfig[] = [
    {key: 'INIT_PUB_CLIENT', label: i18n.t('task.pipeline.INIT_PUB_CLIENT')},
    {key: 'PUB_CLIENT_CONN', label: i18n.t('task.pipeline.PUB_CLIENT_CONN')},
    {key: 'PUB_SUB_CLIENT_READY', label: i18n.t('task.pipeline.PUB_SUB_CLIENT_READY')},
    {key: 'PUB_SUB_CLIENT_START', label: i18n.t('task.pipeline.PUB_SUB_CLIENT_START')},
    {key: 'ONGOING', label: i18n.t('task.pipeline.ONGOING')},
    {key: 'SHUTTING', label: i18n.t('task.pipeline.SHUTTING')},
    {key: 'SHUTDOWN', label: i18n.t('task.pipeline.SHUTDOWN')},
];

export const PUBSUB_SUB_ONLY_PIPELINE_STAGES: PipelineStageConfig[] = [
    {key: 'INIT_SUB_CLIENT', label: i18n.t('task.pipeline.INIT_SUB_CLIENT')},
    {key: 'SUB_CLIENT_CONN', label: i18n.t('task.pipeline.SUB_CLIENT_CONN')},
    {key: 'SUBSCRIBE_CLIENT', label: i18n.t('task.pipeline.SUBSCRIBE_CLIENT')},
    {key: 'PUB_SUB_CLIENT_READY', label: i18n.t('task.pipeline.PUB_SUB_CLIENT_READY')},
    {key: 'ONGOING', label: i18n.t('task.pipeline.ONGOING')},
    {key: 'SHUTTING', label: i18n.t('task.pipeline.SHUTTING')},
    {key: 'SHUTDOWN', label: i18n.t('task.pipeline.SHUTDOWN')},
];

const resolvePipelineStages = (taskType: string, template?: string): PipelineStageConfig[] => {
    if (taskType === 'CONN' || template?.startsWith('CONN')) {
        return CONN_PIPELINE_STAGES;
    }
    if (template === TaskTemplateValues.PUBSUB_PUB_ONLY) {
        return PUBSUB_PUB_ONLY_PIPELINE_STAGES;
    }
    if (template === TaskTemplateValues.PUBSUB_SUB_ONLY) {
        return PUBSUB_SUB_ONLY_PIPELINE_STAGES;
    }
    return PUBSUB_PIPELINE_STAGES;
};

export const getPipelineStages = (taskType: string, template?: string): PipelineStageConfig[] => {
    const stages = resolvePipelineStages(taskType, template);
    return stages.map(s => ({...s, label: i18n.t(`task.pipeline.${s.key}`, {defaultValue: s.label})}));
};

export const getPipelineStageLabel = (stageKey: string): string =>
    i18n.t(`task.pipeline.${stageKey}`, {defaultValue: stageKey});

// State to Pipeline stage index mapping (aligned with simplified state machine)
const STAGE_INDEX_MAP: Record<string, Record<string, number>> = {
    CONN: {
        'INIT': 0, 'ASSIGNED': 0,
        'STARTING': 0,
        'ONGOING': 1,
        'SHUTTING': 2,
        'SHUTDOWN': 3, 'STOPPED': 3, 'FAILED': 3, 'TIMEOUT': 3,
        // Legacy state compatibility
        'START': 0, 'INIT_CLIENT': 0, 'CONNECTING': 0,
    },
    PUBSUB: {
        'INIT': 0, 'ASSIGNED': 0,
        'STARTING': 0,
        'ONGOING': 7,
        'SHUTTING': 8,
        'SHUTDOWN': 9, 'STOPPED': 9, 'FAILED': 9, 'TIMEOUT': 9,
        // Legacy state compatibility
        'START': 0, 'INIT_PUB_CLIENT': 0, 'INIT_SUB_CLIENT': 1,
        'PUB_CLIENT_CONN': 2, 'SUB_CLIENT_CONN': 3, 'SUBSCRIBE_CLIENT': 4,
        'PUB_SUB_CLIENT_READY': 5, 'PUB_SUB_CLIENT_START': 6,
    },
    KAFKA: {
        'INIT': 0, 'ASSIGNED': 0,
        'STARTING': 0,
        'ONGOING': 1,
        'SHUTTING': 2,
        'SHUTDOWN': 3, 'STOPPED': 3, 'FAILED': 3, 'TIMEOUT': 3,
        // Legacy state compatibility
        'START': 0, 'INIT_KAFKA_CLIENT': 0, 'PRODUCING': 0,
    },
    DATABASE: {
        'INIT': 0, 'ASSIGNED': 0,
        'STARTING': 0,
        'ONGOING': 1,
        'SHUTTING': 2,
        'SHUTDOWN': 3, 'STOPPED': 3, 'FAILED': 3, 'TIMEOUT': 3,
        // Legacy state compatibility
        'START': 0, 'DATABASE_CONNECTING': 0, 'DATABASE_OPERATING': 0,
    },
};

const PUBSUB_PUB_ONLY_STAGE_INDEX: Record<string, number> = {
    'INIT': 0, 'ASSIGNED': 0, 'STARTING': 0,
    'START': 0, 'INIT_PUB_CLIENT': 0, 'INIT_SUB_CLIENT': 0,
    'PUB_CLIENT_CONN': 1, 'CONN_CLIENT': 1, 'SUB_CLIENT_CONN': 1,
    'PUB_SUB_CLIENT_READY': 2,
    'PUB_SUB_CLIENT_START': 3,
    'ONGOING': 4,
    'SHUTTING': 5,
    'SHUTDOWN': 6, 'STOPPED': 6, 'FAILED': 6, 'TIMEOUT': 6,
};

const PUBSUB_SUB_ONLY_STAGE_INDEX: Record<string, number> = {
    'INIT': 0, 'ASSIGNED': 0, 'STARTING': 0,
    'START': 0, 'INIT_SUB_CLIENT': 0,
    'SUB_CLIENT_CONN': 1, 'CONN_CLIENT': 1, 'PUB_CLIENT_CONN': 1,
    'SUBSCRIBE_CLIENT': 2,
    'PUB_SUB_CLIENT_READY': 3, 'PUB_SUB_CLIENT_START': 3,
    'ONGOING': 4,
    'SHUTTING': 5,
    'SHUTDOWN': 6, 'STOPPED': 6, 'FAILED': 6, 'TIMEOUT': 6,
};

const resolveStageIndexMap = (taskType: string, template?: string): Record<string, number> => {
    if (taskType === 'CONN' || template?.startsWith('CONN')) {
        return STAGE_INDEX_MAP.CONN;
    }
    if (template === TaskTemplateValues.PUBSUB_PUB_ONLY) {
        return PUBSUB_PUB_ONLY_STAGE_INDEX;
    }
    if (template === TaskTemplateValues.PUBSUB_SUB_ONLY) {
        return PUBSUB_SUB_ONLY_STAGE_INDEX;
    }
    return STAGE_INDEX_MAP.PUBSUB;
};

export const getPipelineStageIndex = (taskType: string, currentStage: string, template?: string): number => {
    const typeMap = resolveStageIndexMap(taskType, template);
    if (typeMap[currentStage] !== undefined) {
        return typeMap[currentStage];
    }
    // fallback: search in stage list
    const stages = getPipelineStages(taskType, template);
    return stages.findIndex(s => s.key === currentStage);
};

// ================== Metric name formatting ==================

const METRIC_PREFIX = 'bifro_task_metric_';

const stripMetricPrefix = (name: string): string =>
    name.startsWith(METRIC_PREFIX) ? name.slice(METRIC_PREFIX.length) : name;

export const getCounterMetricLabel = (name: string): string => stripMetricPrefix(name);

export const getTimerMetricLabel = (name: string): string => stripMetricPrefix(name);

export const formatDateTime = (value: string | number | Date | null | undefined, options?: Intl.DateTimeFormatOptions): string => {
    if (value === null || value === undefined || value === '') {
        return '-';
    }

    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) {
        return '-';
    }

    return date.toLocaleString(getResolvedLocale(i18n.language), options);
};

// ================== Metric visibility ==================

export const canShowMetrics = (status: string): boolean => {
    return ![TaskStatusValues.INIT, TaskStatusValues.ASSIGNED].includes(status as 'INIT' | 'ASSIGNED');
};

export const isTaskTerminal = (status: string): boolean => {
    return [
        TaskStatusValues.SHUTDOWN,
        TaskStatusValues.STOPPED,
        TaskStatusValues.FAILED,
        TaskStatusValues.TIMEOUT,
    ].includes(status as 'SHUTDOWN' | 'STOPPED' | 'FAILED' | 'TIMEOUT');
};
