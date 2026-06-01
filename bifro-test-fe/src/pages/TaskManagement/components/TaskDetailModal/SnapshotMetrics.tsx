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
import {Card, Col, Divider, Progress, Row, Space, Statistic, Tag, Tooltip} from 'antd';
import {CheckCircleOutlined, CloseCircleOutlined, InfoCircleOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import type {TaskStatistics} from '../../../../features/task';

interface SnapshotMetricsProps {
    statistics: TaskStatistics;
    actualDurationSec?: number;
}

function fmtMs(ms: number | null | undefined): string {
    if (ms == null) return '-';
    if (ms < 1000) return `${ms.toFixed(1)} ms`;
    return `${(ms / 1000).toFixed(2)} s`;
}

function connectSuccessRate(stats: TaskStatistics): { rate: number; color: string } {
    const success = stats.totalConnectSuccess ?? 0;
    const fail = stats.totalConnectException ?? 0;
    const total = success + fail;
    if (total === 0) return {rate: 0, color: '#d9d9d9'};
    const rate = Math.round((success / total) * 10000) / 100;
    const color = rate >= 95 ? '#52c41a' : rate >= 80 ? '#fa8c16' : '#ff4d4f';
    return {rate, color};
}

const SnapshotMetrics: React.FC<SnapshotMetricsProps> = ({statistics: s, actualDurationSec}) => {
    const {t} = useTranslation();
    const {rate, color} = connectSuccessRate(s);
    const hasEndToEnd = s.endToEndLatencyP95 != null;
    const hasPuback = s.pubackLatencyP95 != null;

    return (
        <div>
            {/* ── Task summary ── */}
            <div style={{
                marginBottom: 4,
                fontSize: 13,
                fontWeight: 600,
                color: '#333',
                paddingLeft: 8,
                borderLeft: '3px solid #1677ff'
            }}>
                {t('task.detail.snapshot.summary')}
            </div>
            <Row gutter={12} style={{marginBottom: 20}}>
                <Col span={6}>
                    <Card size="small" bordered={false} style={{background: '#f0f5ff'}}>
                        <Statistic
                            title={t('task.detail.snapshot.actualDuration')}
                            value={actualDurationSec ?? (s.actualDurationMs != null ? Math.round(s.actualDurationMs / 1000) : 0)}
                            suffix={t('task.detail.snapshot.seconds')}
                            valueStyle={{color: '#1677ff', fontSize: 22}}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card size="small" bordered={false} style={{background: '#f6ffed'}}>
                        <Statistic
                            title={t('task.detail.snapshot.totalNodes')}
                            value={s.totalNodes ?? 0}
                            suffix={t('task.detail.snapshot.count')}
                            valueStyle={{color: '#52c41a', fontSize: 22}}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card size="small" bordered={false} style={{background: '#fff7e6'}}>
                        <Statistic
                            title={t('task.detail.snapshot.totalClients')}
                            value={(s.totalAssignedClients ?? 0).toLocaleString()}
                            valueStyle={{color: '#fa8c16', fontSize: 22}}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card size="small" bordered={false} style={{background: '#fff0f6'}}>
                        <Statistic
                            title={t('task.detail.snapshot.clientsCreated')}
                            value={(s.totalClientCreated ?? 0).toLocaleString()}
                            valueStyle={{color: '#eb2f96', fontSize: 22}}
                        />
                        {(s.totalClientFailure ?? 0) > 0 && (
                            <div style={{fontSize: 12, color: '#ff4d4f', marginTop: 4}}>
                                {t('task.detail.snapshot.clientFailure', {count: s.totalClientFailure?.toLocaleString()})}
                            </div>
                        )}
                    </Card>
                </Col>
            </Row>

            {/* ── Connection metrics ── */}
            <div style={{
                marginBottom: 4,
                fontSize: 13,
                fontWeight: 600,
                color: '#333',
                paddingLeft: 8,
                borderLeft: '3px solid #52c41a'
            }}>
                {t('task.detail.snapshot.connMetrics')}
            </div>
            <Row gutter={12} style={{marginBottom: 20}}>
                <Col span={8}>
                    <Card size="small">
                        <div style={{marginBottom: 8, display: 'flex', justifyContent: 'space-between'}}>
                            <span style={{fontSize: 12, color: '#999'}}>{t('task.detail.snapshot.connSuccessRate')}</span>
                            <span style={{fontSize: 18, fontWeight: 700, color}}>{rate.toFixed(1)}%</span>
                        </div>
                        <Progress
                            percent={rate}
                            strokeColor={color}
                            showInfo={false}
                            size="small"
                        />
                        <div style={{marginTop: 8, display: 'flex', gap: 12, fontSize: 12}}>
                            <Space size={4}>
                                <CheckCircleOutlined style={{color: '#52c41a'}}/>
                                <span style={{color: '#52c41a'}}>{(s.totalConnectSuccess ?? 0).toLocaleString()}</span>
                            </Space>
                            <Space size={4}>
                                <CloseCircleOutlined style={{color: '#ff4d4f'}}/>
                                <span
                                    style={{color: '#ff4d4f'}}>{(s.totalConnectException ?? 0).toLocaleString()}</span>
                            </Space>
                        </div>
                    </Card>
                </Col>
                <Col span={8}>
                    <Card size="small">
                        <Statistic
                            title={t('task.detail.snapshot.reconnectCount')}
                            value={(s.totalReconnect ?? 0).toLocaleString()}
                            valueStyle={{color: s.totalReconnect ? '#fa8c16' : '#595959', fontSize: 20}}
                        />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card size="small">
                        <Statistic
                            title={t('task.detail.snapshot.publishCompletion')}
                            value={(s.totalPublishCompletion ?? 0).toLocaleString()}
                            valueStyle={{color: '#722ed1', fontSize: 20}}
                        />
                    </Card>
                </Col>
            </Row>

            {/* ── Message metrics ── */}
            <div style={{
                marginBottom: 4,
                fontSize: 13,
                fontWeight: 600,
                color: '#333',
                paddingLeft: 8,
                borderLeft: '3px solid #722ed1'
            }}>
                {t('task.detail.snapshot.msgMetrics')}
            </div>
            <Row gutter={12} style={{marginBottom: 20}}>
                <Col span={8}>
                    <Card size="small">
                        <Statistic
                            title={t('task.detail.snapshot.totalMsgReceived')}
                            value={(s.totalMessageReceived ?? 0).toLocaleString()}
                            valueStyle={{color: '#1677ff', fontSize: 20}}
                        />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card size="small">
                        <Statistic
                            title={
                                <Space size={4}>
                                    <span>{t('task.detail.snapshot.duplicateMsg')}</span>
                                    <Tooltip title={t('task.detail.snapshot.duplicateMsgTip')}>
                                        <InfoCircleOutlined style={{color: '#bbb', fontSize: 12}}/>
                                    </Tooltip>
                                </Space>
                            }
                            value={(s.totalMessageDuplicate ?? 0).toLocaleString()}
                            valueStyle={{color: s.totalMessageDuplicate ? '#fa8c16' : '#595959', fontSize: 20}}
                        />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card size="small">
                        <div style={{fontSize: 12, color: '#999', marginBottom: 8}}>{t('task.detail.snapshot.duplicateRate')}</div>
                        {(s.totalMessageReceived ?? 0) > 0 ? (
                            <>
                                <div style={{
                                    fontSize: 20,
                                    fontWeight: 700,
                                    color: (s.totalMessageDuplicate ?? 0) > 0 ? '#fa8c16' : '#52c41a'
                                }}>
                                    {((s.totalMessageDuplicate ?? 0) / (s.totalMessageReceived ?? 1) * 100).toFixed(2)}%
                                </div>
                            </>
                        ) : (
                            <div style={{fontSize: 20, color: '#bbb'}}>-</div>
                        )}
                    </Card>
                </Col>
            </Row>

            {/* ── Latency distribution ── */}
            <div style={{
                marginBottom: 4,
                fontSize: 13,
                fontWeight: 600,
                color: '#333',
                paddingLeft: 8,
                borderLeft: '3px solid #fa8c16'
            }}>
                {t('task.detail.snapshot.latencyDist')}
            </div>
            <Row gutter={12} style={{marginBottom: hasEndToEnd || hasPuback ? 12 : 0}}>
                {/* Connection latency */}
                <Col span={hasEndToEnd ? 12 : hasPuback ? 16 : 24}>
                    <Card size="small" title={<span style={{fontSize: 12}}>{t('task.detail.snapshot.connLatency')}</span>}>
                        <Row gutter={8}>
                            {[
                                {label: 'P50', val: s.connectLatencyP50},
                                {label: 'P95', val: s.avgConnectLatencyP95},
                                {label: 'P99', val: s.connectLatencyP99},
                                {label: 'Max', val: s.connectLatencyMax},
                            ].map(({label, val}) => (
                                <Col span={6} key={label}>
                                    <div style={{textAlign: 'center'}}>
                                        <Tag color={
                                            val == null ? 'default' :
                                                val < 100 ? 'green' :
                                                    val < 500 ? 'orange' : 'red'
                                        } style={{margin: 0, width: '100%', textAlign: 'center'}}>
                                            {label}
                                        </Tag>
                                        <div style={{fontSize: 13, fontWeight: 600, marginTop: 4}}>{fmtMs(val)}</div>
                                    </div>
                                </Col>
                            ))}
                        </Row>
                    </Card>
                </Col>

                {/* End-to-end latency (PUBSUB only) */}
                {hasEndToEnd && (
                    <Col span={12}>
                        <Card size="small" title={
                            <Space size={4}>
                                <span style={{fontSize: 12}}>{t('task.detail.snapshot.e2eLatency')}</span>
                                <Tooltip title={t('task.detail.snapshot.e2eLatencyTip')}>
                                    <InfoCircleOutlined style={{color: '#bbb', fontSize: 11}}/>
                                </Tooltip>
                            </Space>
                        }>
                            <Row gutter={8}>
                                {[
                                    {label: 'P50', val: s.endToEndLatencyP50},
                                    {label: 'P95', val: s.endToEndLatencyP95},
                                    {label: 'P99', val: s.endToEndLatencyP99},
                                ].map(({label, val}) => (
                                    <Col span={8} key={label}>
                                        <div style={{textAlign: 'center'}}>
                                            <Tag color={
                                                val == null ? 'default' :
                                                    val < 200 ? 'green' :
                                                        val < 1000 ? 'orange' : 'red'
                                            } style={{margin: 0, width: '100%', textAlign: 'center'}}>
                                                {label}
                                            </Tag>
                                            <div
                                                style={{fontSize: 13, fontWeight: 600, marginTop: 4}}>{fmtMs(val)}</div>
                                        </div>
                                    </Col>
                                ))}
                            </Row>
                        </Card>
                    </Col>
                )}

                {/* Puback latency */}
                {hasPuback && !hasEndToEnd && (
                    <Col span={8}>
                        <Card size="small" title={<span style={{fontSize: 12}}>{t('task.detail.snapshot.pubackLatency')}</span>}>
                            <div style={{textAlign: 'center'}}>
                                <Tag color={
                                    (s.pubackLatencyP95 ?? 0) < 100 ? 'green' :
                                        (s.pubackLatencyP95 ?? 0) < 500 ? 'orange' : 'red'
                                } style={{margin: 0}}>P95</Tag>
                                <div style={{
                                    fontSize: 16,
                                    fontWeight: 600,
                                    marginTop: 4
                                }}>{fmtMs(s.pubackLatencyP95)}</div>
                            </div>
                        </Card>
                    </Col>
                )}
            </Row>

            {hasPuback && hasEndToEnd && (
                <Row gutter={12}>
                    <Col span={8}>
                        <Card size="small" title={<span style={{fontSize: 12}}>{t('task.detail.snapshot.pubackLatencyP95')}</span>}>
                            <div style={{
                                fontSize: 20,
                                fontWeight: 600,
                                color: (s.pubackLatencyP95 ?? 0) < 100 ? '#52c41a' : '#fa8c16'
                            }}>
                                {fmtMs(s.pubackLatencyP95)}
                            </div>
                        </Card>
                    </Col>
                </Row>
            )}

            <Divider style={{margin: '16px 0 0'}}/>
            <div style={{fontSize: 12, color: '#999', textAlign: 'right', marginTop: 4}}>
                {t('task.detail.snapshot.snapshotNote')}
            </div>
        </div>
    );
};

export default SnapshotMetrics;
