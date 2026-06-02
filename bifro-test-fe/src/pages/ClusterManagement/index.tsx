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
import {
    Button,
    Card,
    Col,
    Form,
    InputNumber,
    message,
    Modal,
    Progress,
    Row,
    Space,
    Spin,
    Statistic,
    Switch,
    Table,
    Tag
} from 'antd';
import {
    CheckCircleOutlined,
    CloseCircleOutlined,
    EditOutlined,
    ExclamationCircleOutlined,
    EyeOutlined,
    ReloadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {useTranslation} from 'react-i18next';
import clusterApi from '../../features/cluster';
import type {
    ClusterStatistics,
    LocalPortModeConfig,
    NodeListVO,
    NodeRole,
    NodeStatus,
    NodeTaskInfo
} from '../../features/cluster';

const ClusterManagement: React.FC = () => {
    const {t} = useTranslation();
    const [form] = Form.useForm<LocalPortModeConfig>();
    const [nodes, setNodes] = useState<NodeListVO[]>([]);
    const [clusterStats, setClusterStats] = useState<ClusterStatistics | null>(null);
    const [loading, setLoading] = useState(false);
    const [localPortLoading, setLocalPortLoading] = useState(false);
    const [localPortSaving, setLocalPortSaving] = useState(false);
    const [localPortMode, setLocalPortMode] = useState<LocalPortModeConfig | null>(null);
    const [localPortModalVisible, setLocalPortModalVisible] = useState(false);
    const [detailModalVisible, setDetailModalVisible] = useState(false);
    const [nodeDetail, setNodeDetail] = useState<NodeListVO | null>(null);
    const [nodeTasks, setNodeTasks] = useState<NodeTaskInfo[]>([]);
    const [tasksLoading, setTasksLoading] = useState(false);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const loadClusterData = useCallback(async () => {
        setLoading(true);
        setLocalPortLoading(true);
        try {
            const [nodeList, localPortConfig] = await Promise.all([
                clusterApi.getAllNodes(),
                clusterApi.getLocalPortMode(),
            ]);
            setNodes(nodeList);
            setLocalPortMode(localPortConfig);
            form.setFieldsValue(localPortConfig);

            const stats: ClusterStatistics = {
                totalNodes: nodeList.length,
                onlineNodes: nodeList.filter(n => n.alive).length,
                offlineNodes: nodeList.filter(n => !n.alive).length,
                totalMemory: nodeList.reduce((sum, n) => sum + (n.memory?.total || 0), 0),
                usedMemory: nodeList.reduce((sum, n) => sum + (n.memory?.used || 0), 0),
                averageCpuLoad: nodeList.length > 0
                    ? nodeList.reduce((sum, n) => sum + (n.cpu?.loadAverage || 0), 0) / nodeList.length
                    : 0
            };
            setClusterStats(stats);
        } catch (error) {
            message.error(t('cluster.msg.loadFailed'));
            console.error('Failed to load cluster data:', error);
        } finally {
            setLoading(false);
            setLocalPortLoading(false);
        }
    }, [form, t]);

    const saveLocalPortMode = async (values: LocalPortModeConfig) => {
        setLocalPortSaving(true);
        try {
            const saved = await clusterApi.updateLocalPortMode(values);
            setLocalPortMode(saved);
            form.setFieldsValue(saved);
            setLocalPortModalVisible(false);
            message.success(t('cluster.localPortMode.saveSuccess'));
        } catch (error) {
            message.error(error instanceof Error ? error.message : t('cluster.localPortMode.saveFailed'));
            console.error('Failed to update local port mode:', error);
        } finally {
            setLocalPortSaving(false);
        }
    };

    const loadNodeDetail = async (nodeId: string) => {
        try {
            const detail = await clusterApi.getNode(nodeId);
            setNodeDetail(detail);
        } catch (error) {
            message.error(t('cluster.msg.loadNodeFailed'));
            console.error('Failed to load node detail:', error);
        }
    };

    const loadNodeTasks = async (nodeId: string) => {
        setTasksLoading(true);
        try {
            const tasks = await clusterApi.getNodeTasks(nodeId);
            setNodeTasks(tasks);
        } catch (error) {
            console.error('Failed to load node tasks:', error);
            setNodeTasks([]);
        } finally {
            setTasksLoading(false);
        }
    };

    useEffect(() => {
        loadClusterData();
    }, [loadClusterData]);

    const statusMap: Record<NodeStatus, { text: string; color: string; icon: React.ReactNode }> = {
        ONLINE: {text: t('cluster.status.ONLINE'), color: 'success', icon: <CheckCircleOutlined/>},
        OFFLINE: {text: t('cluster.status.OFFLINE'), color: 'error', icon: <CloseCircleOutlined/>},
        UNSTABLE: {text: t('cluster.status.UNSTABLE'), color: 'warning', icon: <ExclamationCircleOutlined/>},
    };

    const formatBytes = (bytes: number): string => {
        if (!bytes || bytes === 0) {
            return '0 B';
        }
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    };

    const getNodeStatus = (node: NodeListVO): NodeStatus => node.alive ? 'ONLINE' : 'OFFLINE';

    const getUsageRate = (used?: number, total?: number): number => total ? Math.round(((used || 0) / total) * 100) : 0;

    const getCpuRate = (cpu?: NodeListVO['cpu']): number => (
        cpu?.processors ? Math.round((cpu.loadAverage / cpu.processors) * 100) : 0
    );

    const getToneByRate = (rate: number): 'success' | 'warning' | 'danger' => {
        if (rate > 80) {
            return 'danger';
        }
        if (rate > 60) {
            return 'warning';
        }
        return 'success';
    };

    const roleColorMap: Record<NodeRole, string> = {
        CONTROL: 'geekblue',
        WORKER: 'green',
        ALL: 'purple',
        UNKNOWN: 'default',
    };

    const renderRole = (role?: NodeRole) => {
        const normalizedRole = role || 'UNKNOWN';
        return (
            <Tag color={roleColorMap[normalizedRole]}>
                {t(`cluster.role.${normalizedRole}`)}
            </Tag>
        );
    };

    const openLocalPortModal = () => {
        form.setFieldsValue(localPortMode || {enabled: false, startPort: 10000, endPort: 65535});
        setLocalPortModalVisible(true);
    };

    const columns = [
        {
            title: t('cluster.columns.nodeName'),
            dataIndex: 'nodeName',
            key: 'nodeName',
            width: 120,
            ellipsis: true,
        },
        {
            title: t('cluster.columns.nodeId'),
            dataIndex: 'nodeId',
            key: 'nodeId',
            width: 120,
            ellipsis: true,
        },
        {
            title: t('cluster.columns.host'),
            dataIndex: 'host',
            key: 'host',
            width: 120,
        },
        {
            title: t('cluster.columns.role'),
            dataIndex: 'role',
            key: 'role',
            width: 100,
            render: (role: NodeRole | undefined) => renderRole(role),
        },
        {
            title: t('cluster.columns.schedulable'),
            dataIndex: 'schedulable',
            key: 'schedulable',
            width: 110,
            render: (schedulable: boolean) => (
                <Tag color={schedulable ? 'success' : 'default'}>
                    {schedulable ? t('cluster.schedulable.yes') : t('cluster.schedulable.no')}
                </Tag>
            ),
        },
        {
            title: t('cluster.columns.status'),
            key: 'status',
            width: 90,
            render: (_: unknown, record: NodeListVO) => {
                const status = getNodeStatus(record);
                return (
                    <Tag color={statusMap[status]?.color || 'default'} icon={statusMap[status]?.icon}>
                        {statusMap[status]?.text || status}
                    </Tag>
                );
            },
        },
        {
            title: t('cluster.columns.memory'),
            dataIndex: 'memory',
            key: 'memory',
            width: 150,
            render: (memory: NodeListVO['memory']) => {
                if (!memory) {
                    return '-';
                }
                const usageRate = memory.total ? (memory.used / memory.total) * 100 : 0;
                return (
                    <div>
                        <Progress
                            percent={Math.round(usageRate)}
                            size="small"
                            strokeColor={usageRate > 80 ? '#ff4d4f' : usageRate > 60 ? '#faad14' : '#52c41a'}
                        />
                        <div style={{fontSize: '12px', color: '#666'}}>
                            {formatBytes(memory.used)} / {formatBytes(memory.total)}
                        </div>
                    </div>
                );
            },
        },
        {
            title: t('cluster.columns.cpu'),
            dataIndex: 'cpu',
            key: 'cpu',
            width: 150,
            render: (cpu: NodeListVO['cpu'], record: NodeListVO) => {
                if (!cpu) {
                    return '-';
                }
                const normalizedLoadRate = cpu.processors ? (cpu.loadAverage / cpu.processors) * 100 : 0;
                return (
                    <div>
                        <Progress
                            percent={Math.round(normalizedLoadRate)}
                            size="small"
                            strokeColor={normalizedLoadRate > 80 ? '#ff4d4f' : normalizedLoadRate > 60 ? '#faad14' : '#52c41a'}
                        />
                        <div style={{fontSize: '12px', color: '#666'}}>
                            {t('cluster.cpuLoadValue', {load: cpu.loadAverage.toFixed(2), n: cpu.processors})}
                        </div>
                        <div style={{fontSize: '12px', color: '#999'}}>
                            {record.lastHeartbeatAt
                                ? t('cluster.sampledAt', {time: dayjs(record.lastHeartbeatAt).format('YYYY-MM-DD HH:mm:ss')})
                                : '-'}
                        </div>
                    </div>
                );
            },
        },
        {
            title: t('common.updatedAt'),
            dataIndex: 'lastHeartbeatAt',
            key: 'lastHeartbeatAt',
            width: 160,
            render: (ts: number) => ts ? dayjs(ts).format('YYYY-MM-DD HH:mm:ss') : '-',
        },
        {
            title: t('common.actions'),
            key: 'action',
            width: 120,
            fixed: 'right' as const,
            render: (_: unknown, record: NodeListVO) => (
                <Space.Compact size="small">
                    <Button
                        type="link"
                        icon={<EyeOutlined/>}
                        onClick={() => handleViewDetail(record.nodeId)}
                    >
                        {t('common.detail')}
                    </Button>
                </Space.Compact>
            ),
        },
    ];

    const handleViewDetail = (nodeId: string) => {
        setNodeDetail(null);
        setNodeTasks([]);
        setDetailModalVisible(true);
        loadNodeDetail(nodeId);
        loadNodeTasks(nodeId);
    };

    return (
        <div>
            {clusterStats && (
                <Row gutter={16} style={{marginBottom: 24}}>
                    <Col span={6}>
                        <Card>
                            <Statistic
                                title={t('cluster.columns.nodeName')}
                                value={clusterStats.totalNodes}
                                suffix={`/ ${clusterStats.onlineNodes} ${t('common.online')}`}
                            />
                        </Card>
                    </Col>
                    <Col span={6}>
                        <Card>
                            <Statistic
                                title={t('cluster.columns.memory')}
                                value={clusterStats.totalMemory ? Math.round((clusterStats.usedMemory / clusterStats.totalMemory) * 100) : 0}
                                suffix="%"
                            />
                        </Card>
                    </Col>
                    <Col span={6}>
                        <Card>
                            <Statistic
                                title={t('cluster.columns.cpu')}
                                value={clusterStats.averageCpuLoad}
                                precision={2}
                                suffix={t('cluster.loadAvgSuffix')}
                            />
                        </Card>
                    </Col>
                    <Col span={6}>
                        <Card>
                            <Statistic title={t('node.memUsage')} value={formatBytes(clusterStats.totalMemory)}/>
                        </Card>
                    </Col>
                </Row>
            )}

            <Card
                title={t('cluster.localPortMode.title')}
                style={{marginBottom: 24}}
                extra={(
                    <Space>
                        <Tag color={localPortMode?.enabled ? 'success' : 'default'}>
                            {localPortMode?.enabled ? t('task.enabled') : t('task.disabled')}
                        </Tag>
                        <Button icon={<EditOutlined/>} onClick={openLocalPortModal}>
                            {t('common.edit')}
                        </Button>
                    </Space>
                )}
            >
                <Spin spinning={localPortLoading}>
                    <div className="task-report-detail-grid">
                        <div className="task-report-detail-item">
                            <span>{t('common.status')}</span>
                            <Tag color={localPortMode?.enabled ? 'success' : 'default'}>
                                {localPortMode?.enabled ? t('task.enabled') : t('task.disabled')}
                            </Tag>
                        </div>
                        <div className="task-report-detail-item">
                            <span>{t('cluster.localPortMode.startPort')}</span>
                            <strong>{localPortMode?.startPort ?? 10000}</strong>
                        </div>
                        <div className="task-report-detail-item">
                            <span>{t('cluster.localPortMode.endPort')}</span>
                            <strong>{localPortMode?.endPort ?? 65535}</strong>
                        </div>
                    </div>
                    <div style={{marginTop: 12, color: '#666'}}>
                        {t('cluster.localPortMode.description')}
                    </div>
                </Spin>
            </Card>

            <Card>
                <div style={{display: 'flex', justifyContent: 'flex-end', marginBottom: 16}}>
                    <Button icon={<ReloadOutlined/>} onClick={loadClusterData}>{t('common.refresh')}</Button>
                </div>
                <Spin spinning={loading}>
                    <Table
                        columns={columns}
                        dataSource={nodes}
                        rowKey="nodeId"
                        scroll={{x: 1280}}
                        pagination={{
                            current: currentPage,
                            pageSize,
                            showSizeChanger: true,
                            showQuickJumper: true,
                            showTotal: (total) => `${t('common.total')} ${total} ${t('cluster.columns.nodeName')}`,
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
                </Spin>
            </Card>

            <Modal
                title={t('cluster.localPortMode.editTitle')}
                open={localPortModalVisible}
                onCancel={() => setLocalPortModalVisible(false)}
                onOk={() => form.submit()}
                confirmLoading={localPortSaving}
                destroyOnHidden
            >
                <Form
                    form={form}
                    layout="vertical"
                    initialValues={{enabled: false, startPort: 10000, endPort: 65535}}
                    onFinish={saveLocalPortMode}
                >
                    <Form.Item name="enabled" label={t('cluster.localPortMode.enabled')} valuePropName="checked">
                        <Switch/>
                    </Form.Item>
                    <Form.Item
                        name="startPort"
                        label={t('cluster.localPortMode.startPort')}
                        rules={[{required: true, type: 'number', min: 1, max: 65535}]}
                    >
                        <InputNumber min={1} max={65535} style={{width: '100%'}}/>
                    </Form.Item>
                    <Form.Item
                        name="endPort"
                        label={t('cluster.localPortMode.endPort')}
                        rules={[{required: true, type: 'number', min: 1, max: 65535}]}
                    >
                        <InputNumber min={1} max={65535} style={{width: '100%'}}/>
                    </Form.Item>
                </Form>
            </Modal>

            <Modal
                title={`${t('common.detail')} - ${nodeDetail?.nodeName || ''}`}
                open={detailModalVisible}
                onCancel={() => setDetailModalVisible(false)}
                footer={null}
                width={960}
                styles={{body: {padding: '12px 0 0', maxHeight: '75vh', overflowY: 'auto'}}}
            >
                {!nodeDetail ? (
                    <Spin style={{display: 'flex', justifyContent: 'center', padding: 40}}/>
                ) : (
                    <div className="task-report-panel cluster-node-detail-panel">
                        <section className="task-report-hero">
                            <div className="task-report-hero-main">
                                <Space size={8} wrap>
                                    {renderRole(nodeDetail.role)}
                                    <Tag color={nodeDetail.alive ? 'success' : 'error'}>
                                        {nodeDetail.alive ? t('cluster.status.ONLINE') : t('cluster.status.OFFLINE')}
                                    </Tag>
                                    <Tag color={nodeDetail.schedulable ? 'success' : 'default'}>
                                        {nodeDetail.schedulable
                                            ? t('cluster.schedulable.yes')
                                            : t('cluster.schedulable.no')}
                                    </Tag>
                                </Space>
                                <div className="task-report-title ant-typography">
                                    {nodeDetail.nodeName || nodeDetail.nodeId}
                                </div>
                                <div className="task-report-meta">
                                    <span>{t('cluster.columns.nodeId')}: {nodeDetail.nodeId}</span>
                                    <span>{t('cluster.columns.host')}: {nodeDetail.host || '-'}</span>
                                    <span>
                                        {t('common.updatedAt')}: {nodeDetail.lastHeartbeatAt
                                            ? dayjs(nodeDetail.lastHeartbeatAt).format('YYYY-MM-DD HH:mm:ss')
                                            : '-'}
                                    </span>
                                </div>
                            </div>
                            <div className="task-report-hero-metrics">
                                <div className={`task-report-metric task-report-metric-${getToneByRate(getCpuRate(nodeDetail.cpu))}`}>
                                    <div className="task-report-metric-label">{t('cluster.columns.cpu')}</div>
                                    <div className="task-report-metric-value">{getCpuRate(nodeDetail.cpu)}%</div>
                                </div>
                                <div className={`task-report-metric task-report-metric-${getToneByRate(getUsageRate(nodeDetail.memory?.used, nodeDetail.memory?.total))}`}>
                                    <div className="task-report-metric-label">{t('cluster.columns.memory')}</div>
                                    <div className="task-report-metric-value">
                                        {getUsageRate(nodeDetail.memory?.used, nodeDetail.memory?.total)}%
                                    </div>
                                </div>
                                <div className="task-report-metric task-report-metric-accent">
                                    <div className="task-report-metric-label">{t('cluster.columns.taskCount')}</div>
                                    <div className="task-report-metric-value">{nodeTasks.length}</div>
                                </div>
                            </div>
                        </section>

                        <div className="task-report-section-grid">
                            <section className="task-report-section">
                                <div className="task-report-section-title">{t('cluster.columns.cpu')}</div>
                                <div className="task-report-detail-grid">
                                    <div className="task-report-detail-item">
                                        <span>{t('cluster.columns.cpu')}</span>
                                        <strong>{nodeDetail.cpu?.processors || 0}</strong>
                                    </div>
                                    <div className="task-report-detail-item">
                                        <span>{t('cluster.loadAvgSuffix')}</span>
                                        <strong>{nodeDetail.cpu ? nodeDetail.cpu.loadAverage.toFixed(2) : '-'}</strong>
                                    </div>
                                </div>
                                <div style={{marginTop: 12}}>
                                    <Progress
                                        percent={getCpuRate(nodeDetail.cpu)}
                                        strokeColor={getCpuRate(nodeDetail.cpu) > 80
                                            ? '#ff4d4f'
                                            : getCpuRate(nodeDetail.cpu) > 60 ? '#faad14' : '#52c41a'}
                                    />
                                </div>
                            </section>

                            <section className="task-report-section">
                                <div className="task-report-section-title">{t('cluster.columns.memory')}</div>
                                <div className="task-report-detail-grid">
                                    <div className="task-report-detail-item">
                                        <span>{t('cluster.memUsedLabel')}</span>
                                        <strong>{nodeDetail.memory ? formatBytes(nodeDetail.memory.used) : '-'}</strong>
                                    </div>
                                    <div className="task-report-detail-item">
                                        <span>{t('cluster.memTotalLabel')}</span>
                                        <strong>{nodeDetail.memory ? formatBytes(nodeDetail.memory.total) : '-'}</strong>
                                    </div>
                                    <div className="task-report-detail-item">
                                        <span>{t('cluster.memFreeLabel')}</span>
                                        <strong>{nodeDetail.memory ? formatBytes(nodeDetail.memory.free) : '-'}</strong>
                                    </div>
                                    <div className="task-report-detail-item">
                                        <span>{t('cluster.memMaxLabel')}</span>
                                        <strong>{nodeDetail.memory ? formatBytes(nodeDetail.memory.max) : '-'}</strong>
                                    </div>
                                </div>
                                <div style={{marginTop: 12}}>
                                    <Progress
                                        percent={getUsageRate(nodeDetail.memory?.used, nodeDetail.memory?.total)}
                                        strokeColor={getUsageRate(nodeDetail.memory?.used, nodeDetail.memory?.total) > 80
                                            ? '#ff4d4f'
                                            : getUsageRate(nodeDetail.memory?.used, nodeDetail.memory?.total) > 60 ? '#faad14' : '#52c41a'}
                                    />
                                </div>
                            </section>
                        </div>

                        <section className="task-report-section">
                            <div className="task-report-section-title">{t('cluster.columns.taskCount')}</div>
                            <Spin spinning={tasksLoading}>
                                {nodeTasks.length > 0 ? (
                                    <Table
                                        dataSource={nodeTasks}
                                        rowKey="taskId"
                                        size="small"
                                        pagination={false}
                                        scroll={{x: 720}}
                                        columns={[
                                            {
                                                title: t('task.taskName'),
                                                dataIndex: 'taskName',
                                                key: 'taskName',
                                                ellipsis: true,
                                            },
                                            {
                                                title: t('task.taskId'),
                                                dataIndex: 'taskId',
                                                key: 'taskId',
                                                width: 200,
                                                ellipsis: true,
                                            },
                                            {
                                                title: t('common.status'),
                                                dataIndex: 'currentStage',
                                                key: 'currentStage',
                                                width: 100,
                                                render: (stage: string) => {
                                                    const stageMap: Record<string, { text: string; color: string }> = {
                                                        STARTING: {text: t('task.status.STARTING'), color: 'processing'},
                                                        CONNECTING: {text: t('task.status.CONNECTING'), color: 'processing'},
                                                        CONNECTED: {text: t('task.detail.instance.connected'), color: 'success'},
                                                        TESTING: {text: t('task.status.ONGOING'), color: 'processing'},
                                                        COMPLETED: {text: t('task.status.SHUTDOWN'), color: 'success'},
                                                        FAILED: {text: t('task.status.FAILED'), color: 'error'},
                                                        STOPPING: {text: t('task.status.SHUTTING'), color: 'warning'},
                                                    };
                                                    const stageInfo = stageMap[stage] || {
                                                        text: stage,
                                                        color: 'default'
                                                    };
                                                    return <Tag color={stageInfo.color}>{stageInfo.text}</Tag>;
                                                },
                                            },
                                            {
                                                title: t('task.totalClients'),
                                                dataIndex: 'totalClientCount',
                                                key: 'totalClientCount',
                                                width: 100,
                                            },
                                        ]}
                                    />
                                ) : (
                                    <div style={{textAlign: 'center', padding: 20, color: '#999'}}>
                                        {t('common.noData')}
                                    </div>
                                )}
                            </Spin>
                        </section>
                    </div>
                )}
            </Modal>
        </div>
    );
};

export default ClusterManagement;
