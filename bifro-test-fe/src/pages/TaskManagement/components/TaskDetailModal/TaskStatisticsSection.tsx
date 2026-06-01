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
import {Card, Descriptions, Empty, Spin} from 'antd';
import {useTranslation} from 'react-i18next';
import type {TaskDetailResponse, TaskStatistics} from '../../../../features/task';
import type {AggregatedRuntimeMetrics} from '../../../../features/task/model';
import SnapshotMetrics from './SnapshotMetrics';
import RunningMetrics from './RunningMetrics';

interface TaskStatisticsSectionProps {
    statistics: TaskStatistics | null;
    statisticsLoading: boolean;
    runtimeLoading: boolean;
    runtimeMetrics: AggregatedRuntimeMetrics | null;
    metricsFromSnapshot: boolean;
    isTaskRunning: boolean;
    isTaskCompleted: boolean;
    taskDetail: TaskDetailResponse | null;
    onLoadRuntimeMetrics: () => void;
}

const TaskStatisticsSection: React.FC<TaskStatisticsSectionProps> = ({
                                                                         statistics,
                                                                         statisticsLoading,
                                                                         runtimeLoading,
                                                                         runtimeMetrics,
                                                                         isTaskRunning,
                                                                         isTaskCompleted,
                                                                         taskDetail,
                                                                         onLoadRuntimeMetrics,
                                                                     }) => {
    const {t} = useTranslation();
    if (statisticsLoading) {
        return (
            <div style={{display: 'flex', justifyContent: 'center', padding: 60}}>
                <Spin size="large"/>
            </div>
        );
    }

    // ── Running: real-time panel ──
    if (isTaskRunning) {
        return (
            <RunningMetrics
                runtimeMetrics={runtimeMetrics}
                runtimeLoading={runtimeLoading}
                taskDetail={taskDetail}
                onRefresh={onLoadRuntimeMetrics}
            />
        );
    }

    // ── Completed/Stopped: full snapshot display ──
    if (isTaskCompleted && statistics) {
        const startTs = taskDetail?.startTime;
        const endTs = taskDetail?.endTime;
        const actualDurationSec = startTs && endTs
            ? Math.round((endTs - startTs) / 1000)
            : undefined;

        return (
            <div>
                {/* Allocation overview */}
                <div style={{
                    marginBottom: 4,
                    fontSize: 13,
                    fontWeight: 600,
                    color: '#333',
                    paddingLeft: 8,
                    borderLeft: '3px solid #d9d9d9',
                    marginTop: 4
                }}>
                    {t('task.detail.allocation.overview')}
                </div>
                <Card size="small" style={{marginBottom: 20}}>
                    <Descriptions size="small" column={4} bordered={false}>
                        <Descriptions.Item label={t('task.detail.allocation.totalNodes')}>{statistics.totalNodes ?? '-'}</Descriptions.Item>
                        <Descriptions.Item
                            label={t('task.detail.allocation.totalClients')}>{statistics.totalAssignedClients?.toLocaleString() ?? '-'}</Descriptions.Item>
                        <Descriptions.Item label={t('task.detail.allocation.minPerNode')}>{statistics.minClientsPerNode ?? '-'}</Descriptions.Item>
                        <Descriptions.Item label={t('task.detail.allocation.maxPerNode')}>{statistics.maxClientsPerNode ?? '-'}</Descriptions.Item>
                    </Descriptions>
                </Card>

                <SnapshotMetrics statistics={statistics} actualDurationSec={actualDurationSec}/>
            </div>
        );
    }

    // ── Init/Assigned state: show allocation overview only ──
    if (statistics) {
        return (
            <div>
                <div style={{
                    marginBottom: 4,
                    fontSize: 13,
                    fontWeight: 600,
                    color: '#333',
                    paddingLeft: 8,
                    borderLeft: '3px solid #d9d9d9',
                    marginTop: 4
                }}>
                    {t('task.detail.allocation.overview')}
                </div>
                <Card size="small">
                    <Descriptions size="small" column={4} bordered={false}>
                        <Descriptions.Item label={t('task.detail.allocation.totalNodes')}>{statistics.totalNodes ?? '-'}</Descriptions.Item>
                        <Descriptions.Item
                            label={t('task.detail.allocation.totalClients')}>{statistics.totalAssignedClients?.toLocaleString() ?? '-'}</Descriptions.Item>
                        <Descriptions.Item label={t('task.detail.allocation.minPerNode')}>{statistics.minClientsPerNode ?? '-'}</Descriptions.Item>
                        <Descriptions.Item label={t('task.detail.allocation.maxPerNode')}>{statistics.maxClientsPerNode ?? '-'}</Descriptions.Item>
                    </Descriptions>
                </Card>
                <Empty description={t('task.detail.statistics.notStarted')} style={{marginTop: 40}} image={Empty.PRESENTED_IMAGE_SIMPLE}/>
            </div>
        );
    }

    return <Empty description={t('task.detail.statistics.noStats')} style={{padding: 40}} image={Empty.PRESENTED_IMAGE_SIMPLE}/>;
};

export default TaskStatisticsSection;
