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

import {describe, expect, it} from 'vitest'
import {
    canAssignTask,
    canConfirmTask,
    canCopyTask,
    canDeleteTask,
    canEditTask,
    canSelectTask,
    canShowMetrics,
    canStopTask,
    CONN_PIPELINE_STAGES,
    generateCopyTaskName,
    getCounterMetricLabel,
    getPipelineStageIndex,
    getPipelineStageLabel,
    getPipelineStages,
    getStatusColor,
    getStatusText,
    getTaskTypeColor,
    getTaskTypeText,
    getTimerMetricLabel,
    isTaskRunning,
    protocolOptions,
    PUBSUB_PUB_ONLY_PIPELINE_STAGES,
    PUBSUB_SUB_ONLY_PIPELINE_STAGES,
    PUBSUB_PIPELINE_STAGES,
    taskTypeOptions,
} from '../taskUtils'
import {TaskStatusValues, TaskTypeValues} from '../../features/task'

describe('taskUtils | unit tests', () => {
    describe('getStatusText', () => {
        it('getStatusText_withINIT_returnsCreated', () => {
            expect(getStatusText(TaskStatusValues.INIT)).toBe('Created')
        })

        it('getStatusText_withONGOING_returnsRunning', () => {
            expect(getStatusText(TaskStatusValues.ONGOING)).toBe('Running')
        })

        it('getStatusText_withSHUTDOWN_returnsCompleted', () => {
            expect(getStatusText(TaskStatusValues.SHUTDOWN)).toBe('Completed')
        })

        it('getStatusText_withSTOPPED_returnsStopped', () => {
            expect(getStatusText(TaskStatusValues.STOPPED)).toBe('Stopped')
        })

        it('getStatusText_withUnknownStatus_returnsOriginal', () => {
            expect(getStatusText('UNKNOWN')).toBe('UNKNOWN')
        })

        it('getStatusText_withEmptyString_returnsEmpty', () => {
            expect(getStatusText('')).toBe('')
        })
    })

    describe('getStatusColor', () => {
        it('getStatusColor_withINIT_returnsDefault', () => {
            expect(getStatusColor(TaskStatusValues.INIT)).toBe('default')
        })

        it('getStatusColor_withONGOING_returnsProcessing', () => {
            expect(getStatusColor(TaskStatusValues.ONGOING)).toBe('processing')
        })

        it('getStatusColor_withSHUTDOWN_returnsSuccess', () => {
            expect(getStatusColor(TaskStatusValues.SHUTDOWN)).toBe('success')
        })

        it('getStatusColor_withSTOPPED_returnsDefault', () => {
            expect(getStatusColor(TaskStatusValues.STOPPED)).toBe('default')
        })

        it('getStatusColor_withUnknownStatus_returnsDefault', () => {
            expect(getStatusColor('UNKNOWN')).toBe('default')
        })
    })

    describe('getTaskTypeText', () => {
        it('getTaskTypeText_withCONN_returnsConnection', () => {
            expect(getTaskTypeText(TaskTypeValues.CONN)).toBe('Connection')
        })

        it('getTaskTypeText_withPUBSUB_returnsPubSub', () => {
            expect(getTaskTypeText(TaskTypeValues.PUBSUB)).toBe('Pub/Sub')
        })

        it('getTaskTypeText_withUnknownType_returnsOriginal', () => {
            expect(getTaskTypeText('UNKNOWN')).toBe('UNKNOWN')
        })
    })

    describe('getTaskTypeColor', () => {
        it('getTaskTypeColor_withCONN_returnsBlue', () => {
            expect(getTaskTypeColor(TaskTypeValues.CONN)).toBe('blue')
        })

        it('getTaskTypeColor_withPUBSUB_returnsGreen', () => {
            expect(getTaskTypeColor(TaskTypeValues.PUBSUB)).toBe('green')
        })

        it('getTaskTypeColor_withUnknownType_returnsDefault', () => {
            expect(getTaskTypeColor('UNKNOWN')).toBe('default')
        })
    })

    describe('canEditTask', () => {
        it('canEditTask_withINIT_returnsTrue', () => {
            expect(canEditTask(TaskStatusValues.INIT)).toBe(true)
        })

        it('canEditTask_withONGOING_returnsFalse', () => {
            expect(canEditTask(TaskStatusValues.ONGOING)).toBe(false)
        })

        it('canEditTask_withSTOPPED_returnsFalse', () => {
            expect(canEditTask(TaskStatusValues.STOPPED)).toBe(false)
        })
    })

    describe('canAssignTask', () => {
        it('canAssignTask_withINIT_returnsTrue', () => {
            expect(canAssignTask(TaskStatusValues.INIT)).toBe(true)
        })

        it('canAssignTask_withONGOING_returnsFalse', () => {
            expect(canAssignTask(TaskStatusValues.ONGOING)).toBe(false)
        })

        it('canAssignTask_withASSIGNED_returnsFalse', () => {
            expect(canAssignTask(TaskStatusValues.ASSIGNED)).toBe(false)
        })
    })

    describe('canConfirmTask', () => {
        it('canConfirmTask_withASSIGNED_returnsTrue', () => {
            expect(canConfirmTask(TaskStatusValues.ASSIGNED)).toBe(true)
        })
    })

    describe('canStopTask', () => {
        it('canStopTask_withONGOING_returnsTrue', () => {
            expect(canStopTask(TaskStatusValues.ONGOING)).toBe(true)
        })

        it('canStopTask_withSTARTING_returnsTrue', () => {
            expect(canStopTask(TaskStatusValues.STARTING)).toBe(true)
        })

        it('canStopTask_withSTART_returnsTrue', () => {
            expect(canStopTask(TaskStatusValues.START)).toBe(true)
        })

        it('canStopTask_withSHUTTING_returnsFalse', () => {
            expect(canStopTask(TaskStatusValues.SHUTTING)).toBe(false)
        })

        it('canStopTask_withINIT_returnsFalse', () => {
            expect(canStopTask(TaskStatusValues.INIT)).toBe(false)
        })

        it('canStopTask_withSTOPPED_returnsFalse', () => {
            expect(canStopTask(TaskStatusValues.STOPPED)).toBe(false)
        })

        it('canStopTask_withCONNECTING_returnsTrue', () => {
            expect(canStopTask(TaskStatusValues.CONNECTING)).toBe(true)
        })
    })

    describe('isTaskRunning', () => {
        it.each([
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
        ])('isTaskRunning_with%s_returnsTrue', (status) => {
            expect(isTaskRunning(status)).toBe(true)
        })

        it.each([
            TaskStatusValues.INIT,
            TaskStatusValues.STOPPED,
            TaskStatusValues.SHUTDOWN,
        ])('isTaskRunning_with%s_returnsFalse', (status) => {
            expect(isTaskRunning(status)).toBe(false)
        })
    })

    describe('canDeleteTask', () => {
        it('canDeleteTask_withINIT_returnsTrue', () => {
            expect(canDeleteTask(TaskStatusValues.INIT)).toBe(true)
        })

        it('canDeleteTask_withSTOPPED_returnsTrue', () => {
            expect(canDeleteTask(TaskStatusValues.STOPPED)).toBe(true)
        })

        it('canDeleteTask_withSHUTDOWN_returnsTrue', () => {
            expect(canDeleteTask(TaskStatusValues.SHUTDOWN)).toBe(true)
        })

        it('canDeleteTask_withONGOING_returnsFalse', () => {
            expect(canDeleteTask(TaskStatusValues.ONGOING)).toBe(false)
        })

        it('canDeleteTask_withSHUTTING_returnsFalse', () => {
            expect(canDeleteTask(TaskStatusValues.SHUTTING)).toBe(false)
        })

        it('canDeleteTask_withCONNECTING_returnsFalse', () => {
            expect(canDeleteTask(TaskStatusValues.CONNECTING)).toBe(false)
        })
    })

    describe('canSelectTask', () => {
        it('canSelectTask_withINIT_returnsTrue', () => {
            expect(canSelectTask(TaskStatusValues.INIT)).toBe(true)
        })

        it('canSelectTask_withSTOPPED_returnsTrue', () => {
            expect(canSelectTask(TaskStatusValues.STOPPED)).toBe(true)
        })

        it('canSelectTask_withSHUTDOWN_returnsTrue', () => {
            expect(canSelectTask(TaskStatusValues.SHUTDOWN)).toBe(true)
        })

        it('canSelectTask_withONGOING_returnsFalse', () => {
            expect(canSelectTask(TaskStatusValues.ONGOING)).toBe(false)
        })

        it('canSelectTask_withSHUTTING_returnsFalse', () => {
            expect(canSelectTask(TaskStatusValues.SHUTTING)).toBe(false)
        })

        it('canSelectTask_withCONNECTING_returnsFalse', () => {
            expect(canSelectTask(TaskStatusValues.CONNECTING)).toBe(false)
        })
    })

    describe('protocolOptions', () => {
        it('protocolOptions_hasThreeOptions', () => {
            expect(protocolOptions).toHaveLength(3)
        })

        it('protocolOptions_firstOptionIsMQTT31', () => {
            expect(protocolOptions[0]).toEqual({label: 'MQTT 3.1', value: 'MQTT_3_1'})
        })

        it('protocolOptions_secondOptionIsMQTT311', () => {
            expect(protocolOptions[1]).toEqual({label: 'MQTT 3.1.1', value: 'MQTT_3_1_1'})
        })

        it('protocolOptions_thirdOptionIsMQTT50', () => {
            expect(protocolOptions[2]).toEqual({label: 'MQTT 5.0', value: 'MQTT_5_0'})
        })
    })

    describe('taskTypeOptions', () => {
        it('taskTypeOptions_hasTwoOptions', () => {
            expect(taskTypeOptions).toHaveLength(2)
        })

        it('taskTypeOptions_firstOptionIsCONN', () => {
            expect(taskTypeOptions[0]).toEqual({label: 'Connection Test', value: TaskTypeValues.CONN})
        })

        it('taskTypeOptions_secondOptionIsPUBSUB', () => {
            expect(taskTypeOptions[1]).toEqual({label: 'Pub/Sub Test', value: TaskTypeValues.PUBSUB})
        })
    })

    describe('canCopyTask | task copy permission', () => {
        it('canCopyTask_withINIT_returnsTrue', () => {
            expect(canCopyTask(TaskStatusValues.INIT)).toBe(true)
        })

        it('canCopyTask_withSTOPPED_returnsTrue', () => {
            expect(canCopyTask(TaskStatusValues.STOPPED)).toBe(true)
        })

        it('canCopyTask_withSHUTDOWN_returnsTrue', () => {
            expect(canCopyTask(TaskStatusValues.SHUTDOWN)).toBe(true)
        })

        it('canCopyTask_withASSIGNED_returnsTrue', () => {
            expect(canCopyTask(TaskStatusValues.ASSIGNED)).toBe(true)
        })

        it('canCopyTask_withSTART_returnsTrue', () => {
            expect(canCopyTask(TaskStatusValues.START)).toBe(true)
        })

        it('canCopyTask_withCONNECTING_returnsTrue', () => {
            expect(canCopyTask(TaskStatusValues.CONNECTING)).toBe(true)
        })

        it('canCopyTask_withINIT_PUB_CLIENT_returnsTrue', () => {
            expect(canCopyTask(TaskStatusValues.INIT_PUB_CLIENT)).toBe(true)
        })

        it('canCopyTask_withINIT_SUB_CLIENT_returnsTrue', () => {
            expect(canCopyTask(TaskStatusValues.INIT_SUB_CLIENT)).toBe(true)
        })

        it('canCopyTask_withONGOING_returnsTrue', () => {
            expect(canCopyTask(TaskStatusValues.ONGOING)).toBe(true)
        })
    })

    describe('generateCopyTaskName | generate copy task name', () => {
        it('generateCopyTaskName_withSimpleName_addsSuffix', () => {
            expect(generateCopyTaskName('test-task')).toBe('test-task - Copy')
        })

        it('generateCopyTaskName_withLongName_truncatesBaseToFit', () => {
            const longName = 'this-is-a-very-long-task-name-that-needs-truncation-to-show-suffix';
            const result = generateCopyTaskName(longName, 20);
            // Truncate base name to fit suffix, no ellipsis
            expect(result.endsWith(' - Copy')).toBe(true);
            expect(result.length).toBe(20);
        })

        it('generateCopyTaskName_withExistingCopySuffix_incrementsCounter', () => {
            expect(generateCopyTaskName('test-task - Copy')).toBe('test-task - Copy (2)')
        })

        it('generateCopyTaskName_withExistingCopyCounter_incrementsAgain', () => {
            expect(generateCopyTaskName('test-task - Copy (2)')).toBe('test-task - Copy (3)')
        })

        it('generateCopyTaskName_withChineseCopySuffix_incrementsCounter', () => {
            const legacyZhCopySuffix = String.fromCharCode(0x526f, 0x672c);
            expect(generateCopyTaskName(`test-task - ${legacyZhCopySuffix}`)).toBe('test-task - Copy (2)')
        })

        it('generateCopyTaskName_withEmptyString_returnsDefaultSuffix', () => {
            expect(generateCopyTaskName('')).toBe(' - Copy')
        })

        it('generateCopyTaskName_withMaxLength_truncatesCorrectly', () => {
            const baseName = 'test-task';
            const result = generateCopyTaskName(baseName, 10);
            // "test-task" + " - Copy" = 9 + 7 = 16 chars
            expect(result).toBe('tes - Copy');
            expect(result.length).toBe(10);
        })
    })

    describe('getPipelineStages', () => {
        it('getPipelineStages_withCONN_returnsConnStages', () => {
            const stages = getPipelineStages('CONN');
            expect(stages).toEqual(CONN_PIPELINE_STAGES);
            expect(stages.length).toBe(4);
        })

        it('getPipelineStages_withPUBSUB_returnsPubsubStages', () => {
            const stages = getPipelineStages('PUBSUB');
            expect(stages).toEqual(PUBSUB_PIPELINE_STAGES);
            expect(stages.length).toBe(10);
        })

        it('getPipelineStages_withPubOnlyTemplate_returnsPubOnlyStages', () => {
            const stages = getPipelineStages('PUBSUB', 'PUBSUB_PUB_ONLY');
            expect(stages).toEqual(PUBSUB_PUB_ONLY_PIPELINE_STAGES);
            expect(stages.length).toBe(7);
        })

        it('getPipelineStages_withSubOnlyTemplate_returnsSubOnlyStages', () => {
            const stages = getPipelineStages('PUBSUB', 'PUBSUB_SUB_ONLY');
            expect(stages).toEqual(PUBSUB_SUB_ONLY_PIPELINE_STAGES);
            expect(stages.length).toBe(7);
        })

        it('getPipelineStages_withUnknown_defaultsToPubsub', () => {
            const stages = getPipelineStages('UNKNOWN');
            expect(stages).toEqual(PUBSUB_PIPELINE_STAGES);
        })
    })

    describe('getPipelineStageLabel', () => {
        it('getPipelineStageLabel_withONGOING_returnsRunning', () => {
            expect(getPipelineStageLabel('ONGOING')).toBe('Running');
        })

        it('getPipelineStageLabel_withINIT_CLIENT_returnsInitConn', () => {
            expect(getPipelineStageLabel('INIT_CLIENT')).toBe('Initialize Clients');
        })

        it('getPipelineStageLabel_withINIT_PUB_CLIENT_returnsInitPub', () => {
            expect(getPipelineStageLabel('INIT_PUB_CLIENT')).toBe('Initialize Publishers');
        })

        it('getPipelineStageLabel_withUnknown_returnsOriginal', () => {
            expect(getPipelineStageLabel('UNKNOWN_STAGE')).toBe('UNKNOWN_STAGE');
        })
    })

    describe('getPipelineStageIndex', () => {
        it('getPipelineStageIndex_CONN_ONGOING_returns1', () => {
            expect(getPipelineStageIndex('CONN', 'ONGOING')).toBe(1);
        })

        it('getPipelineStageIndex_CONN_INIT_CLIENT_returns0', () => {
            expect(getPipelineStageIndex('CONN', 'INIT_CLIENT')).toBe(0);
        })

        it('getPipelineStageIndex_PUBSUB_ONGOING_returns1', () => {
            expect(getPipelineStageIndex('PUBSUB', 'ONGOING')).toBe(7);
        })

        it('getPipelineStageIndex_PUBSUB_SUBSCRIBE_CLIENT_returns4', () => {
            expect(getPipelineStageIndex('PUBSUB', 'SUBSCRIBE_CLIENT')).toBe(4);
        })

        it('getPipelineStageIndex_pubOnly_ONGOING_returns4', () => {
            expect(getPipelineStageIndex('PUBSUB', 'ONGOING', 'PUBSUB_PUB_ONLY')).toBe(4);
        })

        it('getPipelineStageIndex_subOnly_SUBSCRIBE_CLIENT_returns2', () => {
            expect(getPipelineStageIndex('PUBSUB', 'SUBSCRIBE_CLIENT', 'PUBSUB_SUB_ONLY')).toBe(2);
        })

        it('getPipelineStageIndex_withUnknownStage_returnsNegative1', () => {
            expect(getPipelineStageIndex('CONN', 'UNKNOWN')).toBe(-1);
        })
    })

    describe('getCounterMetricLabel', () => {
        it('getCounterMetricLabel_withConnectSuccess_returnsStrippedLabel', () => {
            // Strip-prefix approach: return string with bifro_task_metric_ prefix removed, no longer maintaining Chinese mapping
            expect(getCounterMetricLabel('bifro_task_metric_connect_success_count')).toBe('connect_success_count');
        })

        it('getCounterMetricLabel_withUnknown_returnsOriginal', () => {
            expect(getCounterMetricLabel('unknown_metric')).toBe('unknown_metric');
        })
    })

    describe('getTimerMetricLabel', () => {
        it('getTimerMetricLabel_withConnectLatency_returnsStrippedLabel', () => {
            // Strip-prefix approach: return string with bifro_task_metric_ prefix removed, no longer maintaining Chinese mapping
            expect(getTimerMetricLabel('bifro_task_metric_connect_latency')).toBe('connect_latency');
        })

        it('getTimerMetricLabel_withUnknown_returnsOriginal', () => {
            expect(getTimerMetricLabel('unknown_timer')).toBe('unknown_timer');
        })
    })

    describe('canShowMetrics', () => {
        it('canShowMetrics_withINIT_returnsFalse', () => {
            expect(canShowMetrics('INIT')).toBe(false);
        })

        it('canShowMetrics_withASSIGNED_returnsFalse', () => {
            expect(canShowMetrics('ASSIGNED')).toBe(false);
        })

        it('canShowMetrics_withSTART_returnsTrue', () => {
            expect(canShowMetrics('START')).toBe(true);
        })

        it('canShowMetrics_withONGOING_returnsTrue', () => {
            expect(canShowMetrics('ONGOING')).toBe(true);
        })

        it('canShowMetrics_withSHUTDOWN_returnsTrue', () => {
            expect(canShowMetrics('SHUTDOWN')).toBe(true);
        })

        it('canShowMetrics_withSTOPPED_returnsTrue', () => {
            expect(canShowMetrics('STOPPED')).toBe(true);
        })
    })
})
