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
import {Card, Col, message, Row, Space, Spin, Statistic, Table, Tag, Typography} from 'antd';
import {
    ApiOutlined,
    CloudOutlined,
    DeploymentUnitOutlined,
    ReloadOutlined,
    ThunderboltOutlined
} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import {taskApi} from '../../features/task';
import {clusterApi} from '../../features/cluster';
import {brokerApi as mqttBrokerApi} from '../../features/broker';
import type {TaskListItem} from '../../features/task';
import {TaskStatusValues} from '../../features/task';

const {Title} = Typography;

const Dashboard: React.FC = () => {
    const {t} = useTranslation();
    const [loading, setLoading] = useState(true);
    const [stats, setStats] = useState({
        totalTasks: 0,
        runningTasks: 0,
        totalNodes: 0,
        onlineNodes: 0,
        totalBrokers: 0,
        enabledBrokers: 0,
    });
    const [recentTasks, setRecentTasks] = useState<TaskListItem[]>([]);

    const fetchDashboardData = async () => {
        setLoading(true);
        try {
            const [tasksRes, nodesRes, brokersRes] = await Promise.all([
                taskApi.getAllTasks(undefined, undefined, undefined, undefined, 1, 10),
                clusterApi.getAllNodes(),
                mqttBrokerApi.getAllBrokers(),
            ]);

            const tasks = tasksRes.content || [];
            const nodes = nodesRes || [];
            const brokers = brokersRes.content || [];

            const runningCount = tasks.filter((t: TaskListItem) => t.status === TaskStatusValues.ONGOING).length;
            const onlineCount = nodes.filter((n: { alive: boolean }) => n.alive).length;
            const enabledCount = brokers.filter((b: { enabled: boolean }) => b.enabled).length;

            setStats({
                totalTasks: tasksRes.totalElements || 0,
                runningTasks: runningCount,
                totalNodes: nodes.length,
                onlineNodes: onlineCount,
                totalBrokers: brokersRes.totalElements || 0,
                enabledBrokers: enabledCount,
            });

            setRecentTasks(tasks);
        } catch (error) {
            message.error(t('home.msg.loadFailed'));
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchDashboardData();
        // Refresh data every 30 seconds
        const interval = setInterval(fetchDashboardData, 30000);
        return () => clearInterval(interval);
    }, []);

    const taskColumns = [
        {
            title: t('home.columns.taskName'),
            dataIndex: 'taskName',
            key: 'taskName',
        },
        {
            title: t('home.columns.type'),
            dataIndex: 'taskType',
            key: 'taskType',
            render: (type: string) => type === 'CONN' ? t('task.typeOptions.conn') : t('task.typeOptions.pubsub'),
        },
        {
            title: t('home.columns.clients'),
            dataIndex: 'totalClientCount',
            key: 'totalClientCount',
        },
        {
            title: t('home.columns.status'),
            dataIndex: 'status',
            key: 'status',
            render: (status: string) => {
                const colorMap: Record<string, string> = {
                    [TaskStatusValues.INIT]: 'default',
                    [TaskStatusValues.ASSIGNED]: 'processing',
                    [TaskStatusValues.START]: 'processing',
                    [TaskStatusValues.CONNECTING]: 'processing',
                    [TaskStatusValues.INIT_PUB_CLIENT]: 'processing',
                    [TaskStatusValues.INIT_SUB_CLIENT]: 'processing',
                    [TaskStatusValues.ONGOING]: 'blue',
                    [TaskStatusValues.SHUTDOWN]: 'success',
                    [TaskStatusValues.STOPPED]: 'warning',
                };
                return <Tag color={colorMap[status] || 'default'}>{t(`task.status.${status}`, {defaultValue: status})}</Tag>;
            },
        },
    ];

    const statsCards = [
        {
            title: t('home.totalTask'),
            value: stats.totalTasks,
            icon: <DeploymentUnitOutlined/>,
            color: '#1890ff',
            description: t('home.totalTask'),
        },
        {
            title: t('home.activeTask'),
            value: stats.runningTasks,
            icon: <ThunderboltOutlined/>,
            color: '#52c41a',
            description: t('home.activeTask'),
        },
        {
            title: t('home.onlineNode'),
            value: stats.onlineNodes,
            suffix: `/${stats.totalNodes}`,
            icon: <CloudOutlined/>,
            color: '#faad14',
            description: t('home.onlineNode'),
        },
        {
            title: t('home.enabledBroker'),
            value: stats.enabledBrokers,
            suffix: `/${stats.totalBrokers}`,
            icon: <ApiOutlined/>,
            color: '#722ed1',
            description: t('home.enabledBroker'),
        },
    ];

    return (
        <Spin spinning={loading}>
            <div>
                <Space style={{marginBottom: 16}}>
                    <Title level={2} style={{margin: 0}}>{t('nav.dashboard')}</Title>
                    <ReloadOutlined onClick={fetchDashboardData} style={{cursor: 'pointer', fontSize: 20}}/>
                </Space>

                {/* Statistics cards */}
                <Row gutter={[16, 16]} style={{marginTop: 24}}>
                    {statsCards.map((stat, index) => (
                        <Col xs={24} sm={12} lg={6} key={index}>
                            <Card>
                                <Statistic
                                    title={stat.title}
                                    value={stat.value}
                                    prefix={stat.icon}
                                    valueStyle={{color: stat.color}}
                                    suffix={stat.suffix}
                                />
                                <div style={{marginTop: 8, color: '#8c8c8c', fontSize: 12}}>
                                    {stat.description}
                                </div>
                            </Card>
                        </Col>
                    ))}
                </Row>

                {/* Recent tasks */}
                <Row style={{marginTop: 24}}>
                    <Col span={24}>
                        <Card
                            title={t('task.title')}
                            extra={
                                <a onClick={() => window.location.hash = '/admin/tasks'}>{t('common.detail')}</a>
                            }
                        >
                            <Table
                                dataSource={recentTasks}
                                columns={taskColumns}
                                rowKey="id"
                                pagination={false}
                                size="small"
                            />
                        </Card>
                    </Col>
                </Row>
            </div>
        </Spin>
    );
};

export default Dashboard;
