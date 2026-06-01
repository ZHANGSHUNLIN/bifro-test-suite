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
import {Alert, Empty, Modal, Spin, Switch, Tag} from 'antd';
import {useTranslation} from 'react-i18next';
import NodeMetricsContent from '../../../../components/NodeMetricsContent';
import {nodeApi} from '../../../../features/node';
import type {NodeMetricsResponse} from '../../../../features/task';

interface NodeMetricsModalProps {
    visible: boolean;
    nodeId: string;
    taskId: string;
    nodeName: string;
    onClose: () => void;
    isTaskCompleted?: boolean;  // whether task is completed
}

const NodeMetricsModal: React.FC<NodeMetricsModalProps> = ({
                                                               visible,
                                                               nodeId,
                                                               taskId,
                                                               nodeName,
                                                               onClose,
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

    // Load data when Modal opens
    useEffect(() => {
        if (visible) {
            fetchMetrics();
        }
    }, [visible, fetchMetrics]);

    // Auto refresh
    useEffect(() => {
        if (!visible || !autoRefresh || isTaskCompleted) return;  // completed task, skip refresh
        const timer = setInterval(fetchMetrics, 10000);
        return () => clearInterval(timer);
    }, [visible, autoRefresh, fetchMetrics, isTaskCompleted]);

    const renderContent = () => {
        if (!metrics) {
            return loading ?
                <div style={{display: 'flex', justifyContent: 'center', padding: 40}}><Spin spinning/></div> : null;
        }

        if (!metrics.success) {
            if (metrics.errorCode === 'NODE_OFFLINE') {
                return <Alert type="error" message={t('task.detail.nodeMetrics.nodeOffline')} showIcon/>;
            }
            if (metrics.errorCode === 'QUERY_TIMEOUT') {
                return <Alert type="warning" message={t('task.detail.nodeMetrics.queryTimeout')} showIcon/>;
            }
            return <Alert type="error" message={metrics.errorMessage || t('task.detail.nodeMetrics.queryFailed')} showIcon/>;
        }

        const hasCounterData = metrics.counterMetrics && metrics.counterMetrics.length > 0;
        const hasTimerData = metrics.timerMetrics && metrics.timerMetrics.length > 0;
        const hasData = hasCounterData || hasTimerData;

        if (!hasData) {
            return <Empty description={t('metrics.noData')}/>;
        }

        return (
            <NodeMetricsContent
                counterMetrics={metrics.counterMetrics}
                timerMetrics={metrics.timerMetrics}
            />
        );
    };

    return (
        <Modal
            title={
                <div style={{display: 'flex', alignItems: 'center', gap: 8}}>
                    <Tag color="blue">{nodeId}</Tag>
                    {nodeName && nodeName !== '-' && <span>{nodeName}</span>}
                </div>
            }
            open={visible}
            onCancel={onClose}
            footer={null}
            width={1180}
        >
            <div style={{marginBottom: 16}}>
                {!isTaskCompleted && (
                    <>
                        <span>{t('common.autoRefresh')}: </span>
                        <Switch size="small" checked={autoRefresh} onChange={setAutoRefresh}/>
                    </>
                )}
            </div>
            <Spin spinning={loading}>
                {renderContent()}
            </Spin>
        </Modal>
    );
};

export default NodeMetricsModal;
