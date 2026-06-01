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

import React, {useCallback, useEffect, useState} from 'react';
import {message, Modal, Spin, Table, Tabs, Tag} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useTranslation} from 'react-i18next';
import type {ClientInstance, ClientInstanceResponse} from '../../../../features/task';
import nodeApi from '../../../../features/node';
import {formatDateTime} from '../../../../utils/taskUtils';

interface InstanceDetailModalProps {
    visible: boolean;
    nodeId: string;
    taskId: string;
    nodeName: string;
    onClose: () => void;
}

const InstanceDetailModal: React.FC<InstanceDetailModalProps> = ({
                                                                     visible,
                                                                     nodeId,
                                                                     taskId,
                                                                     nodeName,
                                                                     onClose,
                                                                 }) => {
    const {t} = useTranslation();
    const [activeTab, setActiveTab] = useState('conn');
    const [loading, setLoading] = useState(false);
    const [data, setData] = useState<ClientInstance[]>([]);
    const [pagination, setPagination] = useState({
        current: 1,
        pageSize: 20,
        total: 0,
    });

    const loadData = useCallback((clientType: string, page: number, size: number = pagination.pageSize) => {
        setLoading(true);
        nodeApi.getClientInstances(nodeId, taskId, clientType, page - 1, size)
            .then((res: ClientInstanceResponse) => {
                if (res.success) {
                    setData(res.clients || []);
                    setPagination({
                        current: res.page + 1,
                        pageSize: res.size,
                        total: res.total,
                    });
                } else {
                    message.error(res.errorMessage || t('task.detail.instance.queryFailed'));
                    setData([]);
                }
            })
            .catch((err) => {
                console.error('Failed to load client instances:', err);
                message.error(t('task.detail.instance.loadFailed'));
                setData([]);
            })
            .finally(() => {
                setLoading(false);
            });
    }, [nodeId, taskId, pagination.pageSize]);

    useEffect(() => {
        if (visible) {
            loadData(activeTab, 1);
        }
    }, [visible, activeTab, loadData]);

    const handleTabChange = (key: string) => {
        setActiveTab(key);
    };

    const handleTableChange = (page: number, size: number) => {
        loadData(activeTab, page, size);
    };

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'CONNECTED':
                return 'success';
            case 'CONNECTING':
                return 'processing';
            case 'CONNECTED_FAILED':
                return 'error';
            case 'CLOSED':
                return 'default';
            default:
                return 'default';
        }
    };

    const getStatusText = (status: string) => {
        switch (status) {
            case 'CONNECTED':
                return t('task.detail.instance.connected');
            case 'CONNECTING':
                return t('task.detail.instance.connecting');
            case 'CONNECTED_FAILED':
                return t('task.detail.instance.failed');
            case 'CLOSED':
                return t('task.detail.instance.closed');
            default:
                return status;
        }
    };

    const formatTime = (timestamp: number | undefined) => formatDateTime(timestamp, {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
    });

    const renderLocalEndpoint = (record: ClientInstance) => {
        if (!record.localAddress && !record.localPort) {
            return '-';
        }
        return `${record.localAddress || '-'}:${record.localPort || '-'}`;
    };
    const tableScroll = {x: 1040};

    const columns: ColumnsType<ClientInstance> = [
        {
            title: t('task.detail.instance.columns.clientId'),
            dataIndex: 'clientId',
            key: 'clientId',
            width: 200,
            ellipsis: true,
        },
        {
            title: t('common.host'),
            dataIndex: 'host',
            key: 'host',
            width: 150,
            ellipsis: true,
        },
        {
            title: t('common.port'),
            dataIndex: 'port',
            key: 'port',
            width: 80,
        },
        {
            title: t('task.detail.instance.columns.localEndpoint'),
            key: 'localEndpoint',
            width: 180,
            render: (_, record) => renderLocalEndpoint(record),
            ellipsis: true,
        },
        {
            title: t('task.detail.instance.columns.status'),
            dataIndex: 'status',
            key: 'status',
            width: 100,
            render: (status: string) => (
                <Tag color={getStatusColor(status)}>{getStatusText(status)}</Tag>
            ),
        },
        {
            title: t('task.detail.instance.columns.connectTime'),
            dataIndex: 'connectedAt',
            key: 'connectedAt',
            width: 180,
            render: (time: number) => formatTime(time),
        },
        {
            title: t('task.detail.instance.pubRecv'),
            key: 'messageCount',
            width: 150,
            render: (_, record) => {
                if (record.clientType === 'pub') {
                    return <span>{record.pubCount ?? '-'}</span>;
                }
                if (record.clientType === 'sub') {
                    return <span>{record.subCount ?? '-'}</span>;
                }
                return <span>-</span>;
            },
        },
    ];

    const tabItems = [
        {
            key: 'conn',
            label: t('task.detail.instance.connClients'),
            children: (
                <Table<ClientInstance>
                    dataSource={data}
                    columns={columns}
                    rowKey="clientId"
                    loading={loading}
                    pagination={{
                        current: pagination.current,
                        pageSize: pagination.pageSize,
                        total: pagination.total,
                        onChange: handleTableChange,
                        onShowSizeChange: (_current, size) => {
                            loadData(activeTab, 1, size);
                        },
                        showSizeChanger: true,
                        showQuickJumper: true,
                        showTotal: (total) => `${t('common.total')} ${total}`,
                    }}
                    size="small"
                    scroll={tableScroll}
                />
            ),
        },
        {
            key: 'pub',
            label: t('task.detail.instance.pubClients'),
            children: (
                <Table<ClientInstance>
                    dataSource={data}
                    columns={columns}
                    rowKey="clientId"
                    loading={loading}
                    pagination={{
                        current: pagination.current,
                        pageSize: pagination.pageSize,
                        total: pagination.total,
                        onChange: handleTableChange,
                        onShowSizeChange: (_current, size) => {
                            loadData(activeTab, 1, size);
                        },
                        showSizeChanger: true,
                        showQuickJumper: true,
                        showTotal: (total) => `${t('common.total')} ${total}`,
                    }}
                    size="small"
                    scroll={tableScroll}
                />
            ),
        },
        {
            key: 'sub',
            label: t('task.detail.instance.subClients'),
            children: (
                <Table<ClientInstance>
                    dataSource={data}
                    columns={columns}
                    rowKey="clientId"
                    loading={loading}
                    pagination={{
                        current: pagination.current,
                        pageSize: pagination.pageSize,
                        total: pagination.total,
                        onChange: handleTableChange,
                        onShowSizeChange: (_current, size) => {
                            loadData(activeTab, 1, size);
                        },
                        showSizeChanger: true,
                        showQuickJumper: true,
                        showTotal: (total) => `${t('common.total')} ${total}`,
                    }}
                    size="small"
                    scroll={tableScroll}
                />
            ),
        },
    ];

    return (
        <Modal
            title={t('task.detail.instance.title', {nodeName})}
            open={visible}
            onCancel={onClose}
            footer={null}
            width={1100}
            style={{maxWidth: 'calc(100vw - 32px)'}}
        >
            {loading && pagination.total === 0 ? (
                <Spin style={{display: 'flex', justifyContent: 'center', padding: 40}}/>
            ) : (
                <Tabs activeKey={activeTab} items={tabItems} onChange={handleTabChange}/>
            )}
        </Modal>
    );
};

export default InstanceDetailModal;
