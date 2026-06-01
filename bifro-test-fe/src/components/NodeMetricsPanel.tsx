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
import {Alert, Button, Empty, Space, Spin, Switch, Tag} from 'antd';
import {ArrowLeftOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import NodeMetricsContent from './NodeMetricsContent';
import {nodeApi} from '../features/node';
import type {NodeMetricsResponse} from '../features/task';

interface NodeMetricsPanelProps {
    nodeId: string;
    taskId: string;
    nodeName: string;
    onBack: () => void;
    isTaskCompleted?: boolean;  // added: whether task is completed
}

const NodeMetricsPanel: React.FC<NodeMetricsPanelProps> = ({
                                                               nodeId,
                                                               taskId,
                                                               nodeName,
                                                               onBack,
                                                               isTaskCompleted = false
                                                           }) => {
    const {t} = useTranslation();
    const [metrics, setMetrics] = useState<NodeMetricsResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const [autoRefresh, setAutoRefresh] = useState(!isTaskCompleted);  // completed tasks: default no refresh

    const fetchMetrics = useCallback(async () => {
        setLoading(true);
        try {
            const data = await nodeApi.getNodeMetrics(nodeId, taskId);
            setMetrics(data);
        } catch (error) {
            console.error('Failed to fetch node metrics:', error);
        } finally {
            setLoading(false);
        }
    }, [nodeId, taskId]);

    useEffect(() => {
        fetchMetrics();
    }, [fetchMetrics]);

    useEffect(() => {
        if (!autoRefresh || isTaskCompleted) return;  // completed task, skip refresh
        const timer = setInterval(fetchMetrics, 10000);
        return () => clearInterval(timer);
    }, [autoRefresh, fetchMetrics, isTaskCompleted]);

    const renderContent = () => {
        if (!metrics) {
            return loading ? <Spin spinning/> : null;
        }

        if (!metrics.success) {
            if (metrics.errorCode === 'NODE_OFFLINE') {
                return <Alert type="error" message={t('metrics.nodeOffline')} showIcon/>;
            }
            if (metrics.errorCode === 'QUERY_TIMEOUT') {
                return <Alert type="warning" message={t('metrics.queryTimeout')} showIcon/>;
            }
            return <Alert type="error" message={metrics.errorMessage || t('metrics.queryFailed')} showIcon/>;
        }

        const hasCounterData = metrics.counterMetrics && metrics.counterMetrics.length > 0;
        const hasTimerData = metrics.timerMetrics && metrics.timerMetrics.length > 0;

        if (!hasCounterData && !hasTimerData) {
            return <Empty description={t('metrics.noMetrics')}/>;
        }

        return (
            <NodeMetricsContent
                counterMetrics={metrics.counterMetrics}
                timerMetrics={metrics.timerMetrics}
            />
        );
    };

    return (
        <div>
            <Space style={{marginBottom: 16}}>
                <Button icon={<ArrowLeftOutlined/>} onClick={onBack}>{t('metrics.backToList')}</Button>
                <Tag color="blue">{nodeId}</Tag>
                {nodeName && nodeName !== '-' && <Tag>{nodeName}</Tag>}
                {!isTaskCompleted && (
                    <>
                        <span style={{marginLeft: 8}}>{t('common.autoRefresh')}</span>
                        <Switch size="small" checked={autoRefresh} onChange={setAutoRefresh}/>
                    </>
                )}
            </Space>
            <Spin spinning={loading}>
                {renderContent()}
            </Spin>
        </div>
    );
};

export default NodeMetricsPanel;
