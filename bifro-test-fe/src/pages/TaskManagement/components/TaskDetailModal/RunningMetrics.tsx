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
import {Button, Card, Col, Progress, Row, Select, Space, Statistic, Switch, Tag, Tooltip} from 'antd';
import {InfoCircleOutlined, ReloadOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import type {AggregatedRuntimeMetrics} from '../../../../features/task/model';
import type {TaskDetailResponse} from '../../../../features/task';
import {formatDateTime} from '../../../../utils/taskUtils';

interface RunningMetricsProps {
    runtimeMetrics: AggregatedRuntimeMetrics | null;
    runtimeLoading: boolean;
    taskDetail: TaskDetailResponse | null;
    onRefresh: () => void;
}

const RunningMetrics: React.FC<RunningMetricsProps> = ({
                                                           runtimeMetrics,
                                                           runtimeLoading,
                                                           taskDetail,
                                                           onRefresh,
                                                       }) => {
    const {t} = useTranslation();
    const refreshOptions = [
        {label: t('task.detail.running.5s'), value: 5},
        {label: t('task.detail.running.10s'), value: 10},
        {label: t('task.detail.running.30s'), value: 30},
    ];
    const [autoRefresh, setAutoRefresh] = useState(true);
    const [refreshInterval, setRefreshInterval] = useState(10);
    const [lastUpdateTime, setLastUpdateTime] = useState<number | null>(null);
    const [elapsed, setElapsed] = useState(0);

    // Initial load
    useEffect(() => {
        onRefresh();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Auto refresh timer
    useEffect(() => {
        if (!autoRefresh) return;
        const timer = setInterval(() => onRefresh(), refreshInterval * 1000);
        return () => clearInterval(timer);
    }, [autoRefresh, refreshInterval, onRefresh]);

    // Update last refresh time
    useEffect(() => {
        if (runtimeMetrics) setLastUpdateTime(Date.now());
    }, [runtimeMetrics]);

    // Real-time elapsed duration timer
    useEffect(() => {
        const startTs = taskDetail?.startTime;
        if (!startTs) return;
        const tick = () => setElapsed(Math.round((Date.now() - startTs) / 1000));
        tick();
        const timer = setInterval(tick, 1000);
        return () => clearInterval(timer);
    }, [taskDetail?.startTime]);

    const duration = taskDetail?.mainTaskView?.stressDurationInSec ?? 0;
    const progress = duration > 0 ? Math.min(100, Math.round((elapsed / duration) * 100)) : 0;
    const remaining = duration > 0 ? Math.max(0, duration - elapsed) : null;

    const connectRate = runtimeMetrics?.connectSuccessRate ?? 0;
    const connectRateColor = connectRate >= 95 ? '#52c41a' : connectRate >= 80 ? '#fa8c16' : '#ff4d4f';

    const handleManualRefresh = useCallback(() => {
        onRefresh();
        setLastUpdateTime(Date.now());
    }, [onRefresh]);

    return (
        <div>
            {/* ── Refresh control bar ── */}
            <div style={{
                marginBottom: 16,
                padding: '8px 12px',
                background: '#f5f5f5',
                borderRadius: 6,
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
            }}>
                <Space>
                    <Switch
                        size="small"
                        checked={autoRefresh}
                        onChange={setAutoRefresh}
                        checkedChildren={t('common.autoRefresh')}
                        unCheckedChildren={t('common.manualRefresh')}
                    />
                    {autoRefresh && (
                        <Select
                            size="small"
                            value={refreshInterval}
                            onChange={setRefreshInterval}
                            options={refreshOptions}
                            style={{width: 80}}
                            bordered={false}
                        />
                    )}
                    <Tag color="processing" style={{margin: 0}}>{t('task.detail.running.running')}</Tag>
                </Space>
                <Space>
                    {lastUpdateTime && (
                        <span style={{fontSize: 12, color: '#999'}}>
                            {t('common.lastUpdated')} {formatDateTime(lastUpdateTime, {hour: '2-digit', minute: '2-digit', second: '2-digit'})}
                        </span>
                    )}
                    <Button
                        size="small"
                        type="text"
                        icon={<ReloadOutlined spin={runtimeLoading}/>}
                        onClick={handleManualRefresh}
                        loading={runtimeLoading}
                    >
                        {t('common.refresh')}
                    </Button>
                </Space>
            </div>

            {/* ── Task progress ── */}
            {duration > 0 && (
                <div style={{marginBottom: 20}}>
                    <div style={{
                        marginBottom: 4,
                        fontSize: 13,
                        fontWeight: 600,
                        color: '#333',
                        paddingLeft: 8,
                        borderLeft: '3px solid #1677ff'
                    }}>
                        {t('task.detail.running.taskProgress')}
                    </div>
                    <Card size="small">
                        <div style={{display: 'flex', justifyContent: 'space-between', marginBottom: 8}}>
                            <span style={{fontSize: 13, color: '#666'}}>
                                {t('task.detail.running.elapsed', {elapsed, duration})}
                            </span>
                            {remaining !== null && (
                                <span style={{fontSize: 13, color: '#999'}}>
                                    {t('task.detail.running.remaining', {remaining})}
                                </span>
                            )}
                        </div>
                        <Progress
                            percent={progress}
                            strokeColor={progress >= 100 ? '#52c41a' : '#1677ff'}
                            size="small"
                        />
                    </Card>
                </div>
            )}

            {/* ── Connection metrics ── */}
            <div style={{
                marginBottom: 4,
                fontSize: 13,
                fontWeight: 600,
                color: '#333',
                paddingLeft: 8,
                borderLeft: '3px solid #52c41a'
            }}>
                {t('task.detail.running.connMetrics')}
            </div>
            <Row gutter={12} style={{marginBottom: 20}}>
                <Col span={8}>
                    <Card size="small">
                        <div style={{
                            marginBottom: 6,
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center'
                        }}>
                            <span style={{fontSize: 12, color: '#999'}}>{t('task.detail.running.connSuccessRate')}</span>
                            <span style={{fontSize: 20, fontWeight: 700, color: connectRateColor}}>
                                {connectRate.toFixed(1)}%
                            </span>
                        </div>
                        <Progress
                            percent={connectRate}
                            strokeColor={connectRateColor}
                            showInfo={false}
                            size="small"
                        />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card size="small">
                        <Statistic
                            title={t('task.detail.running.msgReceived')}
                            value={(runtimeMetrics?.messageReceivedCount ?? 0).toLocaleString()}
                            valueStyle={{color: '#722ed1', fontSize: 20}}
                        />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card size="small">
                        <Statistic
                            title={
                                <Space size={4}>
                                    <span>{t('task.detail.running.p95ConnLatency')}</span>
                                    <Tooltip title={t('task.detail.running.p95ConnLatencyTip')}>
                                        <InfoCircleOutlined style={{color: '#bbb', fontSize: 11}}/>
                                    </Tooltip>
                                </Space>
                            }
                            value={runtimeMetrics?.p95Latency != null
                                ? runtimeMetrics.p95Latency < 1000
                                    ? `${runtimeMetrics.p95Latency.toFixed(1)} ms`
                                    : `${(runtimeMetrics.p95Latency / 1000).toFixed(2)} s`
                                : '-'
                            }
                            valueStyle={{
                                color: (runtimeMetrics?.p95Latency ?? 0) < 100 ? '#52c41a' : '#fa8c16',
                                fontSize: 20,
                            }}
                        />
                    </Card>
                </Col>
            </Row>

            {/* ── Node overview ── */}
            <div style={{
                marginBottom: 4,
                fontSize: 13,
                fontWeight: 600,
                color: '#333',
                paddingLeft: 8,
                borderLeft: '3px solid #1677ff'
            }}>
                {t('task.detail.running.nodeOverview')}
            </div>
            <Row gutter={12}>
                <Col span={8}>
                    <Card size="small">
                        <Statistic
                            title={t('home.onlineNode')}
                            value={runtimeMetrics?.onlineNodes ?? 0}
                            suffix={`/ ${runtimeMetrics?.totalNodes ?? 0}`}
                            valueStyle={{
                                color: runtimeMetrics?.onlineNodes === runtimeMetrics?.totalNodes
                                    ? '#52c41a' : '#fa8c16',
                                fontSize: 20,
                            }}
                        />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card size="small">
                        <Statistic
                            title={t('task.detail.allocation.totalClients')}
                            value={(
                                taskDetail?.mainTaskView?.totalClientCount ?? 0
                            ).toLocaleString()}
                            valueStyle={{color: '#1677ff', fontSize: 20}}
                        />
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default RunningMetrics;
