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

import React from 'react';
import {Steps} from 'antd';
import type {PipelineStageInfo, PipelineStageSnapshot} from '../features/task';
import {getPipelineStageIndex, getPipelineStages} from '../utils/taskUtils';

interface PipelineStepsProps {
    // Backend-driven mode (legacy): pass stage list and current index directly
    stages?: PipelineStageInfo[];
    currentStageIndex?: number;
    // Backend-driven mode (new): pass full snapshot list with real-time status per stage
    snapshots?: PipelineStageSnapshot[];
    // Compatibility mode: pass task type and current stage (for subtask scenarios)
    taskType?: string;
    currentStage?: string;
    template?: string;
}

function snapshotStatusToStep(status: PipelineStageSnapshot['status']): 'wait' | 'process' | 'finish' | 'error' {
    switch (status) {
        case 'DONE':
        case 'SKIPPED':
        case 'CANCELLED':
            return 'finish';
        case 'RUNNING':
            return 'process';
        case 'FAILED':
            return 'error';
        default:
            return 'wait';
    }
}

const PipelineSteps: React.FC<PipelineStepsProps> = ({
                                                         stages,
                                                         currentStageIndex,
                                                         snapshots,
                                                         taskType,
                                                         currentStage,
                                                         template
                                                     }) => {
    // Prefer new snapshot rendering (with real-time status)
    if (snapshots && snapshots.length > 0) {
        const visibleSnapshots = snapshots.filter(s => s.visible !== false);
        if (visibleSnapshots.length === 0) {
            return null;
        }

        const runningIndex = visibleSnapshots.findIndex(s => s.status === 'RUNNING');
        const currentIdx = runningIndex >= 0 ? runningIndex
            : visibleSnapshots.filter(s => s.status === 'DONE' || s.status === 'SKIPPED'
                || s.status === 'CANCELLED').length;

        const items = visibleSnapshots.map(snapshot => ({
            title: snapshot.label,
            status: snapshotStatusToStep(snapshot.status),
            description: snapshot.failureReason,
        }));
        return <Steps size="small" current={currentIdx} items={items}/>;
    }

    // Legacy: backend-provided stage list + current index
    if (stages && stages.length > 0 && currentStageIndex !== undefined) {
        const items = stages.map((stage, index) => {
            let status: 'wait' | 'process' | 'finish' = 'wait';
            if (index < currentStageIndex) {
                status = 'finish';
            } else if (index === currentStageIndex) {
                status = 'process';
            }
            return {
                title: stage.label,
                status,
            };
        });
        return <Steps size="small" current={currentStageIndex} items={items}/>;
    }

    // Compatibility mode: use frontend hardcoded stage list (for subtask scenarios)
    if (taskType && currentStage) {
        const frontendStages = getPipelineStages(taskType, template);
        const frontendIndex = getPipelineStageIndex(taskType, currentStage, template);

        // TERMINAL states: SHUTDOWN, STOPPED, FAILED, TIMEOUT indicate completion
        const terminalStages = ['SHUTDOWN', 'STOPPED', 'FAILED', 'TIMEOUT'];
        const isTerminal = terminalStages.includes(currentStage);

        const items = frontendStages.map((stage, index) => {
            let status: 'wait' | 'process' | 'finish' = 'wait';
            if (isTerminal || index < frontendIndex) {
                status = 'finish';
            } else if (index === frontendIndex) {
                status = 'process';
            }
            return {
                title: stage.label,
                status,
            };
        });

        return <Steps size="small" current={isTerminal ? frontendStages.length : frontendIndex} items={items}/>;
    }

    return null;
};

export default PipelineSteps;
