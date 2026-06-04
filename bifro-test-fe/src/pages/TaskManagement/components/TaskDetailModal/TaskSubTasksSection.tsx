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

import React, {useCallback, useMemo, useState} from 'react';
import {Button, Input, Modal, Radio, Space, Table, Tag, Tooltip} from 'antd';
import {
    ApartmentOutlined,
    BarChartOutlined,
    DatabaseOutlined,
    LineChartOutlined,
    ReloadOutlined,
    SearchOutlined
} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import NodeMetricsContent from '../../../../components/NodeMetricsContent';
import type {CounterMetricData, SubTaskDetail, TaskDetailResponse, TimerMetricData} from '../../../../features/task';
import {
    canShowMetrics,
    getStatusColor,
    getStatusText,
    getTaskTypeColor,
    getTaskTypeText,
    isTaskTerminal
} from '../../../../utils/taskUtils';
import TaskStateFlowSection from './TaskStateFlowSection';

export interface SubTaskRow {
    key: string;
    nodeId: string;
    nodeName: string;
    taskType: string;
    totalClientCount: number;
    taskWorkStage: string;
    counterMetrics?: CounterMetricData[];
    timerMetrics?: TimerMetricData[];
}

interface TaskSubTasksSectionProps {
    subTaskDetails: Record<string, SubTaskDetail>;
    taskDetail?: TaskDetailResponse | null;
    subTasksLoading?: boolean;
    onShowMetrics: (record: SubTaskRow) => void;
    onShowInstances: (record: SubTaskRow) => void;
    onRefresh?: () => void;
}

const MetricsModalContent: React.FC<{ record: SubTaskRow }> = ({record}) => {
    const {t} = useTranslation();
    const counters = record.counterMetrics ?? [];
    const timers = record.timerMetrics ?? [];

    if (counters.length === 0 && !timers.some(timer => timer.hasData)) {
        return <div
            style={{padding: '24px 0', textAlign: 'center', color: '#999'}}>{t('task.detail.subtask.noData')}</div>;
    }

    return <NodeMetricsContent counterMetrics={counters} timerMetrics={timers} compact/>;
};

const TaskSubTasksSection: React.FC<TaskSubTasksSectionProps> = ({
                                                                     subTaskDetails,
                                                                     taskDetail,
                                                                     subTasksLoading,
                                                                     onShowMetrics,
                                                                     onShowInstances,
                                                                     onRefresh,
                                                                 }) => {
    const {t} = useTranslation();
    const [filterStatus, setFilterStatus] = useState<string>('');
    const [filterNodeName, setFilterNodeName] = useState<string>('');
    const [metricsModalRecord, setMetricsModalRecord] = useState<SubTaskRow | null>(null);
    // Progress Drawer: stores currently selected node ID, null means closed
    const [progressDrawerNodeId, setProgressDrawerNodeId] = useState<string | null>(null);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(15);

    const rawRows: SubTaskRow[] = useMemo(() => {
        return Object.entries(subTaskDetails).map(([nodeId, detail]) => ({
            key: nodeId,
            nodeId,
            nodeName: detail.nodeName,
            taskType: detail.taskType,
            totalClientCount: detail.totalClientCount,
            taskWorkStage: detail.taskWorkStage,
            counterMetrics: detail.counterMetrics as SubTaskRow['counterMetrics'],
            timerMetrics: detail.timerMetrics as SubTaskRow['timerMetrics'],
        }));
    }, [subTaskDetails]);

    // Summary row data
    const summaryRow = useMemo<SubTaskRow>(() => {
        const totalClients = rawRows.reduce((s, r) => s + r.totalClientCount, 0);
        // Aggregate counterMetrics
        const counterMap = new Map<string, number>();
        rawRows.forEach(r => {
            r.counterMetrics?.forEach(c => {
                counterMap.set(c.name, (counterMap.get(c.name) ?? 0) + c.count);
            });
        });
        return {
            key: '__summary__',
            nodeId: '',
            nodeName: t('task.detail.subtask.summary', {count: rawRows.length}),
            taskType: '',
            totalClientCount: totalClients,
            taskWorkStage: '',
            counterMetrics: Array.from(counterMap.entries()).map(([name, count]) => ({name, count, tags: {}})),
        };
    }, [rawRows, t]);

    const filteredRows = useMemo(() => {
        return rawRows.filter(row => {
            const matchStatus = !filterStatus || row.taskWorkStage === filterStatus;
            const matchNode = !filterNodeName ||
                row.nodeId.toLowerCase().includes(filterNodeName.toLowerCase()) ||
                row.nodeName.toLowerCase().includes(filterNodeName.toLowerCase());
            return matchStatus && matchNode;
        });
    }, [rawRows, filterStatus, filterNodeName]);

    const handleClearFilters = useCallback(() => {
        setFilterStatus('');
        setFilterNodeName('');
        setCurrentPage(1);
    }, []);

    const hasSnapshotMetrics = useCallback((record: SubTaskRow) => (
        (record.counterMetrics?.length ?? 0) > 0 ||
        (record.timerMetrics?.some(timer => timer.hasData) ?? false)
    ), []);

    const handleMonitorClick = useCallback((record: SubTaskRow) => {
        if (isTaskTerminal(record.taskWorkStage) && hasSnapshotMetrics(record)) {
            setMetricsModalRecord(record);
            return;
        }
        onShowMetrics(record);
    }, [hasSnapshotMetrics, onShowMetrics]);

    const STATUS_OPTIONS = ['INIT', 'ASSIGNED', 'ONGOING', 'SHUTTING', 'SHUTDOWN', 'STOPPED', 'FAILED'];

    const columns = [
        {
            title: t('task.detail.subtask.nodeColumn'),
            key: 'node',
            width: 200,
            render: (_: unknown, record: SubTaskRow) => (
                record.key === '__summary__'
                    ? <span style={{fontWeight: 600, color: '#333'}}>{record.nodeName}</span>
                    : (
                        <div>
                            <div style={{fontSize: 13, fontWeight: 500}}>{record.nodeName || '-'}</div>
                            <div style={{fontSize: 11, color: '#999', fontFamily: 'monospace'}}>{record.nodeId}</div>
                        </div>
                    )
            ),
        },
        {
            title: t('common.type'),
            dataIndex: 'taskType',
            key: 'taskType',
            width: 90,
            render: (type: string) => type
                ? <Tag color={getTaskTypeColor(type)}>{getTaskTypeText(type)}</Tag>
                : null,
        },
        {
            title: t('task.detail.basicInfo.clientCount'),
            dataIndex: 'totalClientCount',
            key: 'totalClientCount',
            width: 100,
            sorter: (a: SubTaskRow, b: SubTaskRow) => a.totalClientCount - b.totalClientCount,
            render: (count: number) => (
                <span style={{fontWeight: 600, color: '#1677ff'}}>{count.toLocaleString()}</span>
            ),
        },
        {
            title: t('common.status'),
            dataIndex: 'taskWorkStage',
            key: 'taskWorkStage',
            width: 100,
            render: (status: string) => status
                ? <Tag color={getStatusColor(status)}>{getStatusText(status)}</Tag>
                : null,
        },
        {
            title: t('common.actions'),
            key: 'action',
            width: 220,
            fixed: 'right' as const,
            render: (_: unknown, record: SubTaskRow) => {
                if (record.key === '__summary__') return null;
                const enabled = canShowMetrics(record.taskWorkStage);
                const hasMetrics = hasSnapshotMetrics(record);
                return (
                    <Tooltip title={enabled ? '' : t('task.detail.subtask.noRunningTip')}>
                        <Space size="small">
                            <Button
                                type="link" size="small"
                                icon={<ApartmentOutlined/>}
                                onClick={() => setProgressDrawerNodeId(record.nodeId)}
                            >{t('task.detail.subtask.progressBtn')}</Button>
                            {hasMetrics && (
                                <Button
                                    type="link" size="small"
                                    icon={<BarChartOutlined/>}
                                    onClick={() => setMetricsModalRecord(record)}
                                >{t('task.detail.subtask.metricsBtn')}</Button>
                            )}
                            <Button
                                type="link" size="small"
                                icon={<DatabaseOutlined/>}
                                disabled={!enabled}
                                onClick={() => onShowInstances(record)}
                            >{t('task.detail.subtask.instanceBtn')}</Button>
                            <Button
                                type="link" size="small"
                                icon={<LineChartOutlined/>}
                                disabled={!enabled}
                                onClick={() => handleMonitorClick(record)}
                            >{t('task.detail.subtask.monitorBtn')}</Button>
                        </Space>
                    </Tooltip>
                );
            },
        },
    ];

    if (Object.keys(subTaskDetails).length === 0) {
        return <div style={{textAlign: 'center', color: '#999', padding: 40}}>{t('task.detail.subtask.noSubtasks')}</div>;
    }

    return (
        <div>
            {/* Filter bar */}
            <div style={{
                marginBottom: 12,
                padding: '8px 12px',
                background: '#fafafa',
                borderRadius: 4,
                border: '1px solid #f0f0f0',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: 8,
            }}>
                <Space wrap>
                    <Input
                        placeholder={t('task.detail.subtask.searchNode')}
                        prefix={<SearchOutlined/>}
                        value={filterNodeName}
                        onChange={e => {
                            setFilterNodeName(e.target.value);
                            setCurrentPage(1);
                        }}
                        style={{width: 160}}
                        allowClear
                        size="small"
                    />
                    <Radio.Group
                        size="small"
                        value={filterStatus}
                        onChange={e => {
                            setFilterStatus(e.target.value);
                            setCurrentPage(1);
                        }}
                        optionType="button"
                        buttonStyle="solid"
                        options={[
                            {label: t('task.detail.subtask.allStatus'), value: ''},
                            ...STATUS_OPTIONS.map(s => ({label: getStatusText(s), value: s})),
                        ]}
                    />
                    {(filterNodeName || filterStatus) && (
                        <Button type="link" size="small" onClick={handleClearFilters}>{t('task.detail.subtask.clearFilter')}</Button>
                    )}
                </Space>
                <Space size={8}>
                    <span style={{color: '#999', fontSize: 12}}>{t('task.detail.subtask.nodeCount', {count: filteredRows.length})}</span>
                    {onRefresh && (
                        <Button size="small" icon={<ReloadOutlined/>} onClick={onRefresh} loading={subTasksLoading}>
                            {t('common.refresh')}
                        </Button>
                    )}
                </Space>
            </div>

            <Table<SubTaskRow>
                dataSource={[summaryRow, ...filteredRows]}
                columns={columns}
                size="small"
                pagination={{
                    current: currentPage,
                    pageSize,
                    showSizeChanger: true,
                    showQuickJumper: true,
                    pageSizeOptions: ['15', '30', '50'],
                    showTotal: (total) => `${t('common.total')} ${total}`,
                    onChange: (page, size) => {
                        setCurrentPage(page);
                        setPageSize(size);
                    },
                    onShowSizeChange: (_current, size) => {
                        setCurrentPage(1);
                        setPageSize(size);
                    },
                }}
                scroll={{x: 900}}
                rowClassName={record => record.key === '__summary__' ? 'subtask-summary-row' : ''}
            />

            {/* Metrics detail modal */}
            <Modal
                open={!!metricsModalRecord}
                onCancel={() => setMetricsModalRecord(null)}
                footer={null}
                width="92vw"
                style={{top: 16}}
                styles={{body: {maxHeight: 'calc(100vh - 140px)', overflow: 'auto', paddingRight: 8}}}
                title={
                    <Space>
                        <BarChartOutlined/>
                        <span>{t('task.detail.subtask.nodeMetricsTitle')}</span>
                        {metricsModalRecord && (
                            <Tag color="blue" style={{margin: 0}}>
                                {metricsModalRecord.nodeName || metricsModalRecord.nodeId}
                            </Tag>
                        )}
                    </Space>
                }
                destroyOnClose
            >
                {metricsModalRecord && <MetricsModalContent record={metricsModalRecord}/>}
            </Modal>

            {/* Progress Modal: single-node view, large display */}
            <Modal
                open={progressDrawerNodeId !== null}
                onCancel={() => setProgressDrawerNodeId(null)}
                footer={null}
                width="92vw"
                style={{top: 16}}
                styles={{body: {padding: '0 16px 16px', maxHeight: 'calc(100vh - 120px)', overflowY: 'auto'}}}
                title={
                    <Space>
                        <ApartmentOutlined/>
                        <span>{t('task.detail.subtask.progressTitle')}</span>
                        {progressDrawerNodeId && taskDetail?.subTaskDetails?.[progressDrawerNodeId] && (
                            <Tag color="blue">
                                {taskDetail.subTaskDetails[progressDrawerNodeId].nodeName || progressDrawerNodeId}
                            </Tag>
                        )}
                    </Space>
                }
                destroyOnClose
            >
                <TaskStateFlowSection
                    taskDetail={taskDetail ?? null}
                    subTasksLoading={subTasksLoading}
                    onRefresh={onRefresh}
                    focusNodeId={progressDrawerNodeId ?? undefined}
                />
            </Modal>
        </div>
    );
};

export default TaskSubTasksSection;
