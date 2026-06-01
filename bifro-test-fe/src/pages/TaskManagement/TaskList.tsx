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
import {Button, Empty, Input, message, Popconfirm, Select, Space, Table, Tag, Tooltip} from 'antd';
import type {TableRowSelection} from 'antd/es/table/interface';
import {useTranslation} from 'react-i18next';
import {
    BarChartOutlined,
    CheckCircleOutlined,
    CopyOutlined,
    DeleteOutlined,
    DeploymentUnitOutlined,
    EditOutlined,
    EyeOutlined,
    PlusOutlined,
    ReloadOutlined,
    SearchOutlined,
    StopOutlined,
} from '@ant-design/icons';
import type {TaskListItem} from '../../features/task';
import {TaskStatusValues, TaskTypeValues} from '../../features/task';
import {
    canCopyTask,
    formatDateTime,
    getStatusText,
    getTaskTypeText,
    isTaskRunning,
    selectableStatuses,
    statusConfig,
    taskTypeConfig
} from '../../utils/taskUtils';

interface TaskListProps {
    tasks: TaskListItem[];
    groupSelectOptions?: { label: string; value: string }[];
    initialGroupFilter?: string;
    onViewDetail: (id: string, taskId: string) => void;
    onEdit: (task: TaskListItem) => void;
    onCopy?: (task: TaskListItem) => void;
    onDelete: (id: string) => Promise<void>;
    onConfirm: (id: string) => Promise<void>;
    onAssign: (task: TaskListItem) => void;
    onStop: (id: string) => Promise<void>;
    onBatchDelete: (ids: string[]) => Promise<void>;
    selectedRowKeys?: React.Key[];
    onSelectChange?: (selectedRowKeys: React.Key[]) => void;
    onSearch?: (taskName: string, taskType: string | null, group: string, status: string | null) => string | undefined;
    onAdd?: () => void;
    onRefresh?: () => void;
    onReport?: (task: TaskListItem) => void;
}

const TaskList: React.FC<TaskListProps> = ({
                                               tasks,
                                               groupSelectOptions = [],
                                               initialGroupFilter,
                                               onViewDetail,
                                               onEdit,
                                               onCopy,
                                               onDelete,
                                               onConfirm,
                                               onAssign,
                                               onStop,
                                               onBatchDelete,
                                               selectedRowKeys = [],
                                               onSelectChange,
                                               onSearch,
                                               onAdd,
                                               onRefresh,
                                               onReport,
                                           }) => {
    // Filter state
    const {t} = useTranslation();
    const [taskNameFilter, setTaskNameFilter] = useState<string>('');
    const [taskTypeFilter, setTaskTypeFilter] = useState<string | null>(null);
    const [groupFilter, setGroupFilter] = useState<string | undefined>(undefined);
    const [statusFilter, setStatusFilter] = useState<string | null>(null);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const hasTaskGroups = groupSelectOptions.length > 0;

    // Initial group value setup
    useEffect(() => {
        if (initialGroupFilter) {
            setGroupFilter(initialGroupFilter);
        }
    }, [initialGroupFilter]);

    // Handle search
    const handleSearch = () => {
        if (onSearch) {
            onSearch(taskNameFilter, taskTypeFilter, groupFilter || '', statusFilter);
        }
    };

    // Handle input change (search on Enter)
    const handleTaskNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setTaskNameFilter(e.target.value);
    };

    const handleTaskNamePressEnter = () => {
        handleSearch();
    };

    const handleTaskTypeChange = (value: string | null) => {
        setTaskTypeFilter(value);
        // Auto search when task type changes
        if (onSearch) {
            onSearch(taskNameFilter, value, groupFilter || '', statusFilter);
        }
    };

    const handleGroupChange = (value: string) => {
        setGroupFilter(value);
        if (onSearch) {
            onSearch(taskNameFilter, taskTypeFilter, value, statusFilter);
        }
    };

    const handleStatusChange = (value: string | null) => {
        setStatusFilter(value);
        // Auto search when status changes
        if (onSearch) {
            onSearch(taskNameFilter, taskTypeFilter, groupFilter || '', value);
        }
    };

    // Clear filter
    const handleClearFilters = () => {
        setTaskNameFilter('');
        setTaskTypeFilter(null);
        setStatusFilter(null);
        if (onSearch) {
            const effectiveGroup = onSearch('', null, '', null);
            setGroupFilter(effectiveGroup || undefined);
        }
    };

    const copyText = async (text: string): Promise<void> => {
        if (navigator.clipboard?.writeText && window.isSecureContext) {
            await navigator.clipboard.writeText(text);
            return;
        }

        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.setAttribute('readonly', '');
        textarea.style.position = 'fixed';
        textarea.style.top = '-9999px';
        textarea.style.left = '-9999px';
        document.body.appendChild(textarea);
        textarea.select();
        try {
            const copied = document.execCommand('copy');
            if (!copied) {
                throw new Error('Copy command failed');
            }
        } finally {
            document.body.removeChild(textarea);
        }
    };

    // Table column config
    const columns = [
        {
            title: t('task.taskName'),
            key: 'taskInfo',
            width: 160,
            render: (_: unknown, record: TaskListItem) => {
                const handleCopyName = async () => {
                    try {
                        await copyText(record.taskName || '');
                        message.success(t('task.msg.nameCopied'));
                    } catch {
                        message.error(t('common.copyFailed'));
                    }
                };
                const handleCopyId = async () => {
                    try {
                        await copyText(record.taskId || '');
                        message.success(t('task.msg.idCopied'));
                    } catch {
                        message.error(t('common.copyFailed'));
                    }
                };
                return (
                    <div>
                        <div style={{display: 'flex', alignItems: 'center', gap: 4}}>
                            <div style={{fontWeight: 500}}>{record.taskName}</div>
                            <Tooltip title={t('task.msg.copyNameTooltip')}>
                                <Button
                                    type="text"
                                    size="small"
                                    aria-label={t('task.msg.copyNameTooltip')}
                                    icon={<CopyOutlined/>}
                                    onClick={handleCopyName}
                                    style={{color: '#8c8c8c', padding: '0 2px'}}
                                />
                            </Tooltip>
                        </div>
                        <div style={{display: 'flex', alignItems: 'center', gap: 4}}>
                            <div style={{fontSize: '12px', color: '#8c8c8c'}}>{record.taskId}</div>
                            <Tooltip title={t('task.msg.copyIdTooltip')}>
                                <Button
                                    type="text"
                                    size="small"
                                    aria-label={t('task.msg.copyIdTooltip')}
                                    icon={<CopyOutlined/>}
                                    onClick={handleCopyId}
                                    style={{color: '#8c8c8c', padding: '0 2px'}}
                                />
                            </Tooltip>
                        </div>
                    </div>
                );
            },
        },
        {
            title: t('task.taskType'),
            dataIndex: 'taskType',
            width: 100,
            key: 'taskType',
            render: (type: string) => (
                <Tag color={taskTypeConfig[type]?.color || 'default'}>
                    {getTaskTypeText(type)}
                </Tag>
            ),
        },
        {
            title: t('common.protocol'),
            dataIndex: 'protocol',
            key: 'protocol',
            width: 80,
        },
        {
            title: t('common.host'),
            dataIndex: 'brokers',
            width: 120,
            key: 'brokers',
            render: (brokers: Array<{ host: string, port: number, brokerId?: string, name?: string }>) => (
                <div>
                    {brokers.slice(0, 1).map((broker, index) => (
                        <div key={index} style={{fontSize: '12px'}}>
                            {`${broker.host}:${broker.port}`}
                        </div>
                    ))}
                    {brokers.length > 1 && (
                        <div style={{fontSize: '12px', color: '#999'}}>
                            {t('task.list.moreBrokers', {n: brokers.length - 1})}
                        </div>
                    )}
                </div>
            ),
        },
        {
            title: t('task.form.clientCount'),
            dataIndex: 'totalClientCount',
            key: 'totalClientCount',
            width: 100,
            render: (count: number) => (
                <Tag color="blue">{count}</Tag>
            ),
        },
        {
            title: t('common.status'),
            dataIndex: 'status',
            key: 'status',
            width: 100,
            render: (status: string) => (
                <Tag color={statusConfig[status]?.color || 'default'}>
                    {getStatusText(status)}
                </Tag>
            ),
        },
        {
            title: t('common.createdAt'),
            dataIndex: 'createTimeMs',
            key: 'createTimeMs',
            width: 150,
            render: (_: unknown, record: TaskListItem) => {
                const ts = record.createTimeMs ?? record.createTime;
                return formatDateTime(ts);
            },
        },
        {
            title: t('common.actions'),
            key: 'action',
            width: 360,
            render: (_: unknown, record: TaskListItem) => {
                const status = record.status;
                const isRunning = isTaskRunning(status);
                const isStopping = status === TaskStatusValues.SHUTTING;

                return (
                    <Space.Compact size="small">
                        <Button
                            type="link"
                            icon={<EyeOutlined/>}
                            onClick={() => onViewDetail(record.id, record.taskId)}
                        >
                            {t('common.detail')}
                        </Button>
                        {status === TaskStatusValues.INIT && (
                            <Button
                                type="link"
                                icon={<EditOutlined/>}
                                onClick={() => onEdit(record)}
                            >
                                {t('common.edit')}
                            </Button>
                        )}
                        {onReport && (status === TaskStatusValues.SHUTDOWN || status === TaskStatusValues.STOPPED) && (
                            <Button
                                type="link"
                                icon={<BarChartOutlined/>}
                                onClick={() => onReport(record)}
                            >
                                {t('task.report.title')}
                            </Button>
                        )}
                        {onCopy && canCopyTask(status) && (
                            <Button
                                type="link"
                                icon={<CopyOutlined/>}
                                onClick={() => onCopy(record)}
                            >
                                {t('common.copy')}
                            </Button>
                        )}
                        {(status === TaskStatusValues.INIT || status === TaskStatusValues.ASSIGNED) && (
                            <Button
                                type="link"
                                icon={<DeploymentUnitOutlined/>}
                                onClick={() => onAssign(record)}
                            >
                                {status === TaskStatusValues.ASSIGNED ? t('common.reassign') : t('common.assign')}
                            </Button>
                        )}
                        {status === TaskStatusValues.ASSIGNED && (
                            <Popconfirm
                                title={t('task.list.confirmStart')}
                                description={t('task.list.confirmStartDesc')}
                                onConfirm={() => onConfirm(record.id)}
                                okText={t('common.confirm')}
                                cancelText={t('common.cancel')}
                            >
                                <Button
                                    type="link"
                                    icon={<CheckCircleOutlined/>}
                                >
                                    {t('common.confirm')}
                                </Button>
                            </Popconfirm>
                        )}
                        {isRunning && !isStopping && (
                            <Popconfirm
                                title={t('task.list.confirmStop')}
                                onConfirm={() => onStop(record.id)}
                                okText={t('common.confirm')}
                                cancelText={t('common.cancel')}
                            >
                                <Button type="link" icon={<StopOutlined/>}>
                                    {t('common.stop')}
                                </Button>
                            </Popconfirm>
                        )}
                        {isStopping && (
                            <Button type="link" disabled icon={<StopOutlined/>}>
                                {t('task.status.SHUTTING')}
                            </Button>
                        )}
                        {!isRunning && (
                            <Popconfirm
                                title={t('common.deleteConfirm')}
                                onConfirm={() => onDelete(record.id)}
                                okText={t('common.confirm')}
                                cancelText={t('common.cancel')}
                            >
                                <Button type="link" danger icon={<DeleteOutlined/>}>
                                    {t('common.delete')}
                                </Button>
                            </Popconfirm>
                        )}
                    </Space.Compact>
                );
            },
        },
    ];

    // Table row selection config
    const rowSelection: TableRowSelection<TaskListItem> = {
        selectedRowKeys,
        onChange: onSelectChange,
        getCheckboxProps: (record: TaskListItem) => ({
            disabled: !selectableStatuses.includes(record.status as any),
            title: !selectableStatuses.includes(record.status as any) ? t('task.list.statusNotDeletable') : undefined,
        }),
    };

    return (
        <div>
            <div style={{marginBottom: 16, overflowX: 'auto', paddingBottom: 4}}>
                <div style={{display: 'flex', gap: 12, justifyContent: 'space-between', minWidth: 980}}>
                <div style={{display: 'flex', gap: 12, flex: 1, minWidth: 0}}>
                    <Input
                        placeholder={t('task.searchTaskName')}
                        value={taskNameFilter}
                        onChange={handleTaskNameChange}
                        onPressEnter={handleTaskNamePressEnter}
                        style={{width: 200}}
                        allowClear
                        prefix={<SearchOutlined style={{color: '#bfbfbf'}}/>}
                    />
                    <Select
                        placeholder={t('common.group')}
                        value={groupFilter}
                        onChange={handleGroupChange}
                        style={{width: 150}}
                        options={groupSelectOptions}
                        disabled={!hasTaskGroups}
                    />
                    <Select
                        placeholder={t('task.taskType')}
                        value={taskTypeFilter}
                        onChange={handleTaskTypeChange}
                        style={{width: 140}}
                        allowClear
                        options={[
                            {label: t('task.type.CONN'), value: TaskTypeValues.CONN},
                            {label: t('task.type.PUBSUB'), value: TaskTypeValues.PUBSUB},
                            {label: t('task.type.CHAOS'), value: TaskTypeValues.CHAOS},
                        ]}
                    />
                    <Select
                        placeholder={t('common.status')}
                        value={statusFilter}
                        onChange={handleStatusChange}
                        style={{width: 130}}
                        allowClear
                        options={[
                            {label: t('task.status.INIT'), value: 'INIT'},
                            {label: t('task.status.ASSIGNED'), value: 'ASSIGNED'},
                            {label: t('task.status.STARTING'), value: 'STARTING'},
                            {label: t('task.status.ONGOING'), value: 'ONGOING'},
                            {label: t('task.status.SHUTTING'), value: 'SHUTTING'},
                            {label: t('task.status.SHUTDOWN'), value: 'SHUTDOWN'},
                            {label: t('task.status.STOPPED'), value: 'STOPPED'},
                            {label: t('task.status.FAILED'), value: 'FAILED'},
                            {label: t('task.status.TIMEOUT'), value: 'TIMEOUT'},
                        ]}
                    />
                    <Button type="primary" icon={<SearchOutlined/>} onClick={handleSearch}>{t('common.search')}</Button>
                    {(taskNameFilter || taskTypeFilter || statusFilter || groupFilter) && (
                        <Button onClick={handleClearFilters}>{t('common.reset')}</Button>
                    )}
                    {selectedRowKeys.length > 0 && onBatchDelete && (
                        <Popconfirm
                            title={t('common.deleteConfirm')}
                            onConfirm={() => onBatchDelete(selectedRowKeys as string[])}
                            okText={t('common.confirm')}
                            cancelText={t('common.cancel')}
                        >
                            <Button danger icon={<DeleteOutlined/>}>
                                {t('task.batchDelete')} ({selectedRowKeys.length})
                            </Button>
                        </Popconfirm>
                    )}
                </div>
                <Space>
                    {onRefresh && (
                        <Button icon={<ReloadOutlined/>} onClick={onRefresh}>
                            {t('common.refresh')}
                        </Button>
                    )}
                    {onAdd && (
                        <Button type="primary" icon={<PlusOutlined/>} onClick={onAdd} disabled={!hasTaskGroups}>
                            {t('task.createTask')}
                        </Button>
                    )}
                </Space>
                </div>
            </div>
            <Table
                columns={columns}
                dataSource={tasks}
                rowKey="id"
                rowSelection={rowSelection}
                scroll={{x: 1220}}
                locale={{
                    emptyText: hasTaskGroups
                        ? undefined
                        : <Empty description={t('task.msg.noTaskGroups')}/>
                }}
                pagination={{
                    current: currentPage,
                    pageSize,
                    showSizeChanger: true,
                    showQuickJumper: true,
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
            />
        </div>
    );
};

export default TaskList;
