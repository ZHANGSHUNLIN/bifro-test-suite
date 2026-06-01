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

import React, {useEffect, useState} from 'react';
import {Alert, Card, Space, Spin, Tag, Timeline, Typography} from 'antd';
import {ClockCircleOutlined, InfoCircleOutlined, SyncOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import type {StateHistoryItem, SubTaskDetail, TaskDetailResponse} from '../../../../features/task';
import {formatDateTime, getStatusText} from '../../../../utils/taskUtils';
import PipelineSteps from '../../../../components/PipelineSteps';
import {taskApi} from '../../../../features/task';

interface TaskStateFlowSectionProps {
    taskDetail: TaskDetailResponse | null;
    subTasksLoading?: boolean;
    onSubTaskDetailsExpand?: (expanded: boolean) => void;
    onRefresh?: () => void;
    
    focusNodeId?: string;
}

const dedupeStateHistory = (items: StateHistoryItem[]): StateHistoryItem[] => {
    const seen = new Set<string>();
    const result: StateHistoryItem[] = [];
    for (const item of items) {
        if (!item.fromStage || !item.toStage || !item.timestamp) {
            continue;
        }
        const key = [
            item.fromStage ?? '',
            item.toStage ?? '',
            item.triggerEvent ?? '',
            String(item.timestamp ?? ''),
            item.nodeId ?? '',
        ].join('|');
        if (seen.has(key)) continue;
        seen.add(key);
        result.push(item);
    }
    return result;
};

const TaskStateFlowSection: React.FC<TaskStateFlowSectionProps> = ({
                                                                       taskDetail,
                                                                       subTasksLoading = false,
                                                                       onRefresh,
                                                                       focusNodeId,
                                                                   }) => {
    const {t} = useTranslation();
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [lastRefreshTime, setLastRefreshTime] = useState<number | null>(null);
    // nodeId → state history list
    const [stateHistoryMap, setStateHistoryMap] = useState<Record<string, StateHistoryItem[]>>({});
    const taskStage = taskDetail?.mainTaskView?.taskWorkStage;

    // Auto refresh mechanism
    useEffect(() => {
        if (!taskStage) return;

        const isTerminal = ['SHUTDOWN', 'STOPPED', 'FAILED', 'TIMEOUT'].includes(taskStage);
        if (isTerminal) return;

        const timer = setInterval(() => {
            if (onRefresh) {
                setIsRefreshing(true);
                onRefresh();
                setTimeout(() => {
                    setIsRefreshing(false);
                    setLastRefreshTime(Date.now());
                }, 500);
            }
        }, 15000); // Auto refresh every 15s

        return () => clearInterval(timer);
    }, [taskStage, onRefresh]);

    // Immediately refresh when all pipeline snapshots complete to avoid 15s visual lag
    useEffect(() => {
        if (!taskDetail?.subTaskDetails || !onRefresh) return;
        const isTerminal = ['SHUTDOWN', 'STOPPED', 'FAILED', 'TIMEOUT'].includes(taskStage ?? '');
        if (isTerminal) return;

        const allDone = Object.values(taskDetail.subTaskDetails).every(detail => {
            const stages = detail.pipelineStages;
            if (!stages || stages.length === 0) return false;
            return stages.every(s => s.status === 'DONE' || s.status === 'SKIPPED' || s.status === 'FAILED');
        });

        if (allDone) {
            const t = setTimeout(() => {
                onRefresh();
            }, 800); // Small delay to wait for backend taskWorkStage write to complete
            return () => clearTimeout(t);
        }
    }, [taskDetail?.subTaskDetails, taskStage, onRefresh]);

    // Fetch state change history for each node
    useEffect(() => {
        if (!taskDetail?.taskId || !taskDetail.subTaskDetails) return;
        const taskId = taskDetail.taskId;
        const nodeIds = Object.keys(taskDetail.subTaskDetails);
        nodeIds.forEach(nodeId => {
            taskApi.getStateHistory(taskId, nodeId).then(data => {
                if (data) {
                    setStateHistoryMap(prev => ({...prev, [nodeId]: dedupeStateHistory(data)}));
                }
            }).catch(() => {/* ignore */
            });
        });
    }, [taskDetail?.taskId, taskDetail?.subTaskDetails]);

    if (!taskDetail?.mainTaskView) return null;

    // Render state flow diagram for a single node
    const renderNodeStateFlow = (detail: SubTaskDetail) => {
        const history = stateHistoryMap[detail.nodeId] || [];

        // Prefer backend pipeline snapshot rendering (snapshots with real-time status)
        const pipelineContent = detail.pipelineStages && detail.pipelineStages.length > 0
            ? <PipelineSteps snapshots={detail.pipelineStages}/>
            : <Typography.Text type="secondary">{t('task.detail.stateFlow.noData')}</Typography.Text>;
        return (
            <Card size="small" style={{marginTop: 8}}>
                <Space direction="vertical" style={{width: '100%'}} size={8}>
                    <Space>
                        <Tag color="blue">{detail.nodeName || detail.nodeId}</Tag>
                        <Tag color={
                            detail.taskWorkStage === 'SHUTDOWN' || detail.taskWorkStage === 'STOPPED' ? 'green' :
                                detail.taskWorkStage === 'FAILED' || detail.taskWorkStage === 'TIMEOUT' ? 'red' :
                                    detail.taskWorkStage === 'ONGOING' ? 'processing' : 'default'
                        }>
                            {getStatusText(detail.taskWorkStage)}
                        </Tag>
                    </Space>
                    {pipelineContent}
                    {history.length > 0 && (
                        <div style={{marginTop: 8, borderTop: '1px solid #f0f0f0', paddingTop: 8}}>
                            <div style={{
                                fontSize: 12,
                                color: '#666',
                                marginBottom: 6,
                                display: 'flex',
                                alignItems: 'center',
                                gap: 4
                            }}>
                                <ClockCircleOutlined/>
                                <span>{t('task.detail.stateFlow.title')}</span>
                            </div>
                            <Timeline
                                style={{fontSize: 12}}
                                items={history.map(h => ({
                                    color: h.toStage === 'FAILED' || h.toStage === 'TIMEOUT' ? 'red'
                                        : h.toStage === 'SHUTDOWN' || h.toStage === 'STOPPED' ? 'green'
                                            : h.toStage === 'ONGOING' ? 'blue' : 'gray',
                                    children: (
                                        <div style={{lineHeight: '1.6'}}>
                                            <Typography.Text style={{fontSize: 12}}>
                                                {getStatusText(h.fromStage || '')}
                                                {' → '}
                                                <strong>{getStatusText(h.toStage || '')}</strong>
                                                {h.triggerEvent && (
                                                    <Tag style={{marginLeft: 4, fontSize: 11}}
                                                         color="default">{h.triggerEvent}</Tag>
                                                )}
                                            </Typography.Text>
                                            <div style={{color: '#999', fontSize: 11}}>
                                                {formatDateTime(h.timestamp)}
                                            </div>
                                        </div>
                                    ),
                                }))}
                            />
                        </div>
                    )}
                </Space>
            </Card>
        );
    };

    const isTaskTerminal = taskStage
        ? ['SHUTDOWN', 'STOPPED', 'FAILED', 'TIMEOUT'].includes(taskStage)
        : false;

    // Show only specified node when focusNodeId is set; otherwise show all
    const visibleEntries = Object.entries(taskDetail.subTaskDetails || {}).filter(
        ([nodeId]) => !focusNodeId || nodeId === focusNodeId
    );
    const subTaskCount = visibleEntries.length;

    return (
        <div style={{padding: '16px 0'}}>
            {/* Refresh control bar: hidden in single-node focus mode */}
            {!focusNodeId && (
                <div style={{
                    marginBottom: 16,
                    padding: 12,
                    background: '#f5f5f5',
                    borderRadius: 4,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center'
                }}>
                    <Space>
                        {isRefreshing && <SyncOutlined spin style={{color: '#1890ff'}}/>}
                        <span style={{color: '#666', fontSize: 14}}>
                            {t('task.detail.running.liveMonitor')}
                            {lastRefreshTime && (
                                <span style={{marginLeft: 8, fontSize: 12, color: '#999'}}>
                                    {t('task.detail.running.lastUpdated')}: {formatDateTime(lastRefreshTime, {hour: '2-digit', minute: '2-digit', second: '2-digit'})}
                                </span>
                            )}
                        </span>
                        {!isTaskTerminal && (
                            <Alert
                                message={t('task.detail.running.autoRefreshEvery')}
                                type="info"
                                showIcon={false}
                                style={{padding: '2px 8px', fontSize: 12}}
                            />
                        )}
                    </Space>
                    {!isTaskTerminal && onRefresh && (
                        <Space>
                            <Tag color="processing" icon={<SyncOutlined spin={isRefreshing}/>}>
                                {t('task.detail.stateFlow.autoRefreshing')}
                            </Tag>
                        </Space>
                    )}
                </div>
            )}

            {/* Execution progress step bar: hidden in single-node focus mode */}
            {!focusNodeId && taskDetail.pipelineStages && taskDetail.pipelineStages.length > 0 && (
                <Card size="small" style={{marginBottom: 16}} title={t('task.detail.stateFlow.execProgress')}>
                    <PipelineSteps
                        stages={taskDetail.pipelineStages}
                        currentStageIndex={taskDetail.currentStageIndex}
                    />
                </Card>
            )}

            {/* Subtask progress */}
            {subTaskCount > 0 ? (
                focusNodeId ? (
                    // Single-node focus: remove outer Card, content fills directly
                    subTasksLoading
                        ? <Spin style={{display: 'flex', justifyContent: 'center', padding: 40}}/>
                        : <>{visibleEntries.map(([nodeId, detail]) => (
                            <div key={nodeId}>
                                {renderNodeStateFlow(detail)}
                            </div>
                        ))}</>
                ) : (
                    <Card
                        size="small"
                        style={{marginBottom: 16}}
                        title={t('task.detail.stateFlow.subtaskOverview', {count: subTaskCount})}
                    >
                        {subTasksLoading ? (
                            <Spin style={{display: 'flex', justifyContent: 'center', padding: 20}}/>
                        ) : (
                            <div style={{display: 'flex', flexDirection: 'column', gap: 12}}>
                                {visibleEntries.map(([nodeId, detail]) => (
                                    <div key={nodeId} style={{
                                        border: '1px solid #f0f0f0',
                                        borderRadius: 4,
                                        padding: 12,
                                        backgroundColor: '#fafafa'
                                    }}>
                                        <Space direction="vertical" style={{width: '100%'}} size={12}>
                                            <div style={{
                                                display: 'flex',
                                                justifyContent: 'space-between',
                                                alignItems: 'center',
                                                flexWrap: 'wrap',
                                                gap: 8
                                            }}>
                                                <Space>
                                                    <Tag color="blue" style={{fontSize: 13, fontWeight: 500}}>
                                                        {detail.nodeName || nodeId}
                                                    </Tag>
                                                    <Tag color={
                                                        detail.taskWorkStage === 'SHUTDOWN' || detail.taskWorkStage === 'STOPPED' ? 'green' :
                                                            detail.taskWorkStage === 'FAILED' || detail.taskWorkStage === 'TIMEOUT' ? 'red' :
                                                                detail.taskWorkStage === 'ONGOING' ? 'processing' : 'default'
                                                    }>
                                                        {getStatusText(detail.taskWorkStage)}
                                                    </Tag>
                                                </Space>
                                                <span style={{color: '#666', fontSize: 13}}>
                                                    <InfoCircleOutlined style={{marginRight: 4}}/>
                                                    {t('task.detail.stateFlow.clientCount', {count: detail.totalClientCount})}
                                                </span>
                                            </div>
                                            {renderNodeStateFlow(detail)}
                                        </Space>
                                    </div>
                                ))}
                            </div>
                        )}
                    </Card>
                )
            ) : (
                <Card size="small" style={{marginBottom: 16}}>
                    <div style={{textAlign: 'center', color: '#999', padding: 20}}>
                        <InfoCircleOutlined style={{fontSize: 24, marginBottom: 12, color: '#d9d9d9'}}/>
                        <div>{t('task.detail.stateFlow.noSubtaskData')}</div>
                        <div style={{fontSize: 12, marginTop: 8}}>{t('task.detail.stateFlow.assignFirst')}</div>
                    </div>
                </Card>
            )}
        </div>
    );
};

export default TaskStateFlowSection;
