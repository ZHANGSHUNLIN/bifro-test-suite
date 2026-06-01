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
import {Button, Card, Col, Empty, message, Progress, Row, Space, Spin, Statistic, Table, Tag, Typography,} from 'antd';
import {
    ApiOutlined,
    CloudOutlined,
    DeploymentUnitOutlined,
    PlusOutlined,
    ReloadOutlined,
    ThunderboltOutlined,
} from '@ant-design/icons';
import {useNavigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import type {ColumnsType} from 'antd/es/table';
import {taskApi} from '../../features/task';
import clusterApi from '../../features/cluster';
import {brokerApi as mqttBrokerApi} from '../../features/broker';
import TaskReportModal from '../../components/TaskReportModal';
import {formatDateTime, getStatusColor, getStatusText, getTaskTypeText} from '../../utils/taskUtils';
import type {TaskListItem} from '../../features/task';
import {TaskStatusValues} from '../../features/task';
import type {NodeListVO} from '../../features/cluster';

const {Title, Text} = Typography;

const TERMINAL_STATUSES: Set<string> = new Set([
    TaskStatusValues.SHUTDOWN,
    TaskStatusValues.STOPPED,
]);

const getProgressColor = (pct: number) =>
    pct > 80 ? '#ff4d4f' : pct > 60 ? '#faad14' : '#00b173';

const Home: React.FC = () => {
    const navigate = useNavigate();
    const {t} = useTranslation();

    const [allTasks, setAllTasks] = useState<TaskListItem[]>([]);
    const [nodes, setNodes] = useState<NodeListVO[]>([]);
    const [totalTasks, setTotalTasks] = useState(0);
    const [totalBrokers, setTotalBrokers] = useState(0);
    const [enabledBrokers, setEnabledBrokers] = useState(0);
    const [statsLoading, setStatsLoading] = useState(true);
    const [taskLoading, setTaskLoading] = useState(true);
    const [nodeLoading, setNodeLoading] = useState(true);
    const [reportTask, setReportTask] = useState<TaskListItem | null>(null);

    const fetchStats = useCallback(async () => {
        setStatsLoading(true);
        try {
            const [taskResp, nodeResp, brokerResp] = await Promise.allSettled([
                taskApi.getAllTasks(undefined, undefined, undefined, undefined, 1, 1),
                clusterApi.getAllNodes(),
                mqttBrokerApi.getAllBrokers(),
            ]);

            if (taskResp.status === 'fulfilled') {
                setTotalTasks(taskResp.value.totalElements);
            }
            if (nodeResp.status === 'fulfilled') {
                setNodes(nodeResp.value);
            }
            if (brokerResp.status === 'fulfilled') {
                const brokers = brokerResp.value.content;
                setTotalBrokers(brokerResp.value.totalElements);
                setEnabledBrokers(brokers.filter(b => b.enabled).length);
            }
        } catch {
            // allSettled handles individual errors, this is a safety net
        } finally {
            setStatsLoading(false);
        }
    }, []);

    const fetchTasks = useCallback(async () => {
        setTaskLoading(true);
        try {
            const resp = await taskApi.getAllTasks(undefined, undefined, undefined, undefined, 1, 100);
            setAllTasks(resp.content);
        } catch {
            // ignore polling errors silently
        } finally {
            setTaskLoading(false);
        }
    }, []);

    const fetchNodes = useCallback(async () => {
        setNodeLoading(true);
        try {
            const nodeResp = await clusterApi.getAllNodes();
            setNodes(nodeResp);
        } catch {
            // ignore polling errors silently
        } finally {
            setNodeLoading(false);
        }
    }, []);

    // Initial load
    useEffect(() => {
        fetchStats();
        fetchTasks();
    }, [fetchStats, fetchTasks]);

    // Task polling 10s
    useEffect(() => {
        const timer = setInterval(fetchTasks, 10_000);
        return () => clearInterval(timer);
    }, [fetchTasks]);

    // Node polling 30s
    useEffect(() => {
        const timer = setInterval(() => {
            fetchNodes();
        }, 30_000);
        return () => clearInterval(timer);
    }, [fetchNodes]);

    const handleRefresh = () => {
        fetchStats();
        fetchTasks();
        message.success(t('common.refreshed'));
    };

    const inProgressTasks = allTasks.filter(t => !TERMINAL_STATUSES.has(t.status));
    const recentDoneTasks = allTasks
        .filter(t => TERMINAL_STATUSES.has(t.status))
        .slice(0, 10);

    const onlineNodes = nodes.filter(n => n.alive).length;

    // Statistics row data
    const stats = [
        {
            title: t('home.activeTask'),
            value: inProgressTasks.length,
            icon: <ThunderboltOutlined style={{fontSize: 24, color: '#1890ff'}}/>,
            color: '#e6f7ff',
        },
        {
            title: t('home.totalTask'),
            value: totalTasks,
            icon: <DeploymentUnitOutlined style={{fontSize: 24, color: '#52c41a'}}/>,
            color: '#f6ffed',
        },
        {
            title: t('home.onlineNode'),
            suffix: `/ ${nodes.length}`,
            value: onlineNodes,
            icon: <CloudOutlined style={{fontSize: 24, color: '#faad14'}}/>,
            color: '#fff7e6',
        },
        {
            title: t('home.enabledBroker'),
            suffix: `/ ${totalBrokers}`,
            value: enabledBrokers,
            icon: <ApiOutlined style={{fontSize: 24, color: '#722ed1'}}/>,
            color: '#f9f0ff',
        },
    ];

    const recentColumns: ColumnsType<TaskListItem> = [
        {
            title: t('home.columns.taskName'),
            dataIndex: 'taskName',
            key: 'taskName',
            ellipsis: true,
            render: (name: string) => name || '-',
        },
        {
            title: t('home.columns.type'),
            dataIndex: 'taskType',
            key: 'taskType',
            width: 100,
            render: (type: string) => (
                <Tag color={type === 'PUBSUB' ? 'green' : 'blue'}>{getTaskTypeText(type)}</Tag>
            ),
        },
        {
            title: t('home.columns.status'),
            dataIndex: 'status',
            key: 'status',
            width: 90,
            render: (status: string) => (
                <Tag color={getStatusColor(status)}>{getStatusText(status)}</Tag>
            ),
        },
        {
            title: t('home.columns.clients'),
            dataIndex: 'totalClientCount',
            key: 'totalClientCount',
            width: 90,
            align: 'right',
        },
        {
            title: t('common.updatedAt'),
            dataIndex: 'updatedAt',
            key: 'updatedAt',
            width: 160,
            render: (t: string) => formatDateTime(t),
        },
        {
            title: t('common.actions'),
            key: 'action',
            width: 80,
            render: (_, record) => (
                <Button
                    size="small"
                    type="link"
                    onClick={() => setReportTask(record)}
                >
                    {t('task.report.title')}
                </Button>
            ),
        },
    ];

    return (
        <div style={{padding: '24px', maxWidth: 1400, margin: '0 auto'}}>
            {/* Page header */}
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24}}>
                <Title level={3} style={{margin: 0}}>{t('nav.home')}</Title>
                <Space>
                    <Button
                        icon={<PlusOutlined/>}
                        type="primary"
                        onClick={() => navigate('/tasks')}
                    >
                        {t('task.createTask')}
                    </Button>
                    <Button icon={<ReloadOutlined/>} onClick={handleRefresh}>
                        {t('common.refresh')}
                    </Button>
                </Space>
            </div>

            {/* ① Statistics row */}
            <Row gutter={[16, 16]} style={{marginBottom: 24}}>
                {stats.map((s, i) => (
                    <Col xs={24} sm={12} lg={6} key={i}>
                        <Card
                            style={{backgroundColor: s.color, borderColor: 'transparent'}}
                            bodyStyle={{padding: 20}}
                        >
                            <Spin spinning={statsLoading} size="small">
                                <div style={{display: 'flex', alignItems: 'center', gap: 12}}>
                                    {s.icon}
                                    <Statistic
                                        title={<Text style={{fontSize: 13}}>{s.title}</Text>}
                                        value={s.value}
                                        suffix={s.suffix}
                                        valueStyle={{fontSize: 28, fontWeight: 600}}
                                    />
                                </div>
                            </Spin>
                        </Card>
                    </Col>
                ))}
            </Row>

            {/* ② Active tasks + ③ Cluster node status */}
            <Row gutter={[16, 16]} style={{marginBottom: 24}}>
                {/* ② Active tasks */}
                <Col xs={24} lg={14}>
                    <Card
                        title={t('home.activeTask')}
                        extra={<Text type="secondary" style={{fontSize: 12}}>{t('home.refreshEvery10s')}</Text>}
                        style={{height: '100%'}}
                    >
                        <Spin spinning={taskLoading}>
                            {inProgressTasks.length === 0 ? (
                                <Empty description={t('common.noData')} image={Empty.PRESENTED_IMAGE_SIMPLE}/>
                            ) : (
                                <div>
                                    {inProgressTasks.slice(0, 5).map(task => (
                                        <div
                                            key={task.taskId}
                                            style={{
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'space-between',
                                                padding: '10px 0',
                                                borderBottom: '1px solid #f0f0f0',
                                            }}
                                        >
                                            <div style={{flex: 1, minWidth: 0, marginRight: 8}}>
                                                <Text
                                                    ellipsis
                                                    style={{display: 'block', fontWeight: 500}}
                                                    title={task.taskName}
                                                >
                                                    {task.taskName || task.taskId}
                                                </Text>
                                                <Text type="secondary" style={{fontSize: 12}}>
                                                    {getTaskTypeText(task.taskType)} · {t('home.clientCount', {count: task.totalClientCount})}
                                                </Text>
                                            </div>
                                            <Tag color={getStatusColor(task.status)}>
                                                {getStatusText(task.status)}
                                            </Tag>
                                        </div>
                                    ))}
                                    {inProgressTasks.length > 5 && (
                                        <div style={{textAlign: 'center', paddingTop: 12}}>
                                            <Button
                                                type="link"
                                                size="small"
                                                onClick={() => navigate('/tasks')}
                                            >
                                                {t('common.detail')} {inProgressTasks.length}
                                            </Button>
                                        </div>
                                    )}
                                </div>
                            )}
                        </Spin>
                    </Card>
                </Col>

                {/* ③ Cluster node status */}
                <Col xs={24} lg={10}>
                    <Card
                        title={t('cluster.title')}
                        extra={<Text type="secondary" style={{fontSize: 12}}>{t('home.refreshEvery30s')}</Text>}
                        style={{height: '100%'}}
                    >
                        <Spin spinning={nodeLoading}>
                            {nodes.length === 0 ? (
                                <Empty description={t('common.noData')} image={Empty.PRESENTED_IMAGE_SIMPLE}/>
                            ) : (
                                <div>
                                    {nodes.map(node => {
                                        const memPct = node.memory
                                            ? Math.round((node.memory.used / node.memory.total) * 100)
                                            : 0;
                                        const cpuPct = node.cpu
                                            ? Math.min(Math.round(node.cpu.loadAverage * 100 / (node.cpu.processors || 1)), 100)
                                            : 0;
                                        return (
                                            <div
                                                key={node.nodeId}
                                                style={{
                                                    padding: '10px 0',
                                                    borderBottom: '1px solid #f0f0f0',
                                                }}
                                            >
                                                <div style={{
                                                    display: 'flex',
                                                    justifyContent: 'space-between',
                                                    marginBottom: 6
                                                }}>
                                                    <Text
                                                        style={{fontWeight: 500}}>{node.nodeName || node.nodeId}</Text>
                                                    <Tag color={node.alive ? 'success' : 'error'}>
                                                        {node.alive ? t('cluster.status.ONLINE') : t('cluster.status.OFFLINE')}
                                                    </Tag>
                                                </div>
                                                <div style={{display: 'flex', gap: 8, alignItems: 'center'}}>
                                                    <Text type="secondary"
                                                          style={{fontSize: 11, width: 28, flexShrink: 0}}>CPU</Text>
                                                    <Progress
                                                        percent={cpuPct}
                                                        size="small"
                                                        showInfo={false}
                                                        strokeColor={getProgressColor(cpuPct)}
                                                        style={{flex: 1, margin: 0}}
                                                    />
                                                    <Text style={{
                                                        fontSize: 11,
                                                        width: 32,
                                                        textAlign: 'right',
                                                        flexShrink: 0
                                                    }}>
                                                        {cpuPct}%
                                                    </Text>
                                                </div>
                                                <div style={{
                                                    display: 'flex',
                                                    gap: 8,
                                                    alignItems: 'center',
                                                    marginTop: 4
                                                }}>
                                                    <Text type="secondary"
                                                          style={{fontSize: 11, width: 28, flexShrink: 0}}>MEM</Text>
                                                    <Progress
                                                        percent={memPct}
                                                        size="small"
                                                        showInfo={false}
                                                        strokeColor={getProgressColor(memPct)}
                                                        style={{flex: 1, margin: 0}}
                                                    />
                                                    <Text style={{
                                                        fontSize: 11,
                                                        width: 32,
                                                        textAlign: 'right',
                                                        flexShrink: 0
                                                    }}>
                                                        {memPct}%
                                                    </Text>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </Spin>
                    </Card>
                </Col>
            </Row>

            {/* ④ Recently completed tasks */}
            <Card title={t('task.status.SHUTDOWN')}>
                <Table<TaskListItem>
                    columns={recentColumns}
                    dataSource={recentDoneTasks}
                    rowKey="taskId"
                    size="small"
                    pagination={false}
                    loading={taskLoading}
                    locale={{emptyText: <Empty description={t('common.noData')} image={Empty.PRESENTED_IMAGE_SIMPLE}/>}}
                />
            </Card>

            {/* TaskReportModal */}
            <TaskReportModal
                open={reportTask !== null}
                taskId={reportTask?.taskId ?? ''}
                taskName={reportTask?.taskName}
                onClose={() => setReportTask(null)}
            />
        </div>
    );
};

export default Home;
