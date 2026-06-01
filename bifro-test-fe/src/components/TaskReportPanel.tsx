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
import {Empty, Progress, Space, Spin, Table, Tag, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import i18n from '../i18n';
import {taskApi} from '../features/task';
import type {NodeReport, TaskReportResponse} from '../features/task';

interface TaskReportPanelProps {
    taskId: string;
    taskType?: string;
}

type ReportKind = 'CONN' | 'PUBSUB' | 'CHAOS';

type MetricTone = 'default' | 'success' | 'warning' | 'danger' | 'accent';

type MetricItem = {
    key: string;
    label: string;
    value: React.ReactNode;
    tone?: MetricTone;
};

type MetricGroup = {
    key: string;
    title: string;
    items: MetricItem[];
};

const normalizeReportKind = (value?: string): ReportKind => {
    if (value === 'CONN' || value === 'PUBSUB' || value === 'CHAOS') {
        return value;
    }
    return 'PUBSUB';
};

const formatNumber = (value?: number) => value != null ? value.toLocaleString() : '-';
const formatFixed = (value?: number, digits = 2) => value != null ? value.toFixed(digits) : '-';
const formatRate = (value?: number, digits = 1) => value != null ? `${value.toFixed(digits)}/s` : '-';
const formatPercent = (value?: number, digits = 2) => value != null ? `${value.toFixed(digits)}%` : '-';
const formatMs = (value?: number) => value != null ? `${value.toFixed(2)} ms` : '-';

const TaskReportPanel: React.FC<TaskReportPanelProps> = ({taskId, taskType}) => {
    const {t} = useTranslation();
    const [report, setReport] = useState<TaskReportResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchReport = async () => {
            setLoading(true);
            setError(null);
            try {
                const data = await taskApi.getTaskReport(taskId);
                setReport(data);
            } catch (e: any) {
                setError(e.message || t('task.detail.reportPanel.loadFailed'));
            } finally {
                setLoading(false);
            }
        };
        fetchReport();
    }, [taskId, t]);

    if (loading) {
        return <Spin style={{display: 'flex', justifyContent: 'center', padding: 40}}/>;
    }

    if (error) {
        return <Empty description={error}/>;
    }

    if (!report) {
        return <Empty description={t('task.detail.reportPanel.noData')}/>;
    }

    // format duration
    const formatDuration = (ms?: number) => {
        if (!ms) return '-';
        const seconds = Math.floor(ms / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);
        if (hours > 0) {
            return i18n.t('profile.duration.hours', {h: hours, m: minutes % 60, s: seconds % 60});
        }
        if (minutes > 0) {
            return i18n.t('profile.duration.minutes', {m: minutes, s: seconds % 60});
        }
        return i18n.t('profile.duration.seconds', {s: seconds});
    };

    const reportKind = normalizeReportKind(report.taskType || taskType);
    const isConnReport = reportKind === 'CONN';
    const isPubsubReport = reportKind === 'PUBSUB';
    const isChaosReport = reportKind === 'CHAOS';
    const hasErrors = (report.totalConnectFailure || 0) > 0
        || (report.failedClients || 0) > 0
        || (report.messageLossRate || 0) > 0
        || Object.keys(report.errorCounts || {}).length > 0;
    const reportHealthColor = hasErrors ? 'gold' : 'green';
    const reportHealthText = hasErrors ? t('report.healthWarning') : t('report.healthNormal');

    const primaryMetrics: MetricItem[] = [
        {
            key: 'duration',
            label: t('report.testDuration'),
            value: formatDuration(report.durationMs),
            tone: 'default',
        },
        {
            key: 'connectSuccessRate',
            label: t('report.connSuccessRate'),
            value: formatPercent(report.connectSuccessRate),
            tone: (report.connectSuccessRate || 0) >= 99 ? 'success' : 'danger',
        },
        {
            key: 'connectQps',
            label: t('report.connQps'),
            value: formatRate(report.avgConnectQps),
            tone: 'accent',
        },
        ...(!isConnReport ? [{
            key: 'messageQps',
            label: t('report.throughput'),
            value: `${formatFixed(report.avgMessagesPerSecond)} msg/s`,
            tone: 'accent' as MetricTone,
        }] : []),
        ...(isPubsubReport ? [{
            key: 'latencyP95',
            label: t('report.latencyP95'),
            value: formatMs(report.latencyP95),
            tone: 'default' as MetricTone,
        }] : []),
        ...(isChaosReport ? [{
            key: 'chaosBehaviors',
            label: t('report.chaosBehaviorCount'),
            value: formatNumber(Object.keys(report.chaosResults || {}).length),
            tone: 'warning' as MetricTone,
        }] : []),
    ];

    const metricGroups: MetricGroup[] = [
        {
            key: 'connection',
            title: t('report.connectionMetrics'),
            items: [
                {
                    key: 'connSuccess',
                    label: t('report.connSuccessCount'),
                    value: formatNumber(report.totalConnectSuccess),
                    tone: 'success',
                },
                {
                    key: 'connFail',
                    label: t('report.connFailCount'),
                    value: formatNumber(report.totalConnectFailure),
                    tone: report.totalConnectFailure ? 'danger' : 'default',
                },
                {key: 'reconnect', label: t('report.reconnectCount'), value: formatNumber(report.totalReconnectCount)},
                {key: 'connLatency', label: t('report.connLatencyP95'), value: formatMs(report.connectLatencyP95)},
                {key: 'connQps', label: t('report.connQps'), value: formatRate(report.avgConnectQps)},
            ],
        },
        {
            key: 'clients',
            title: t('report.clientMetrics'),
            items: [
                {key: 'totalClients', label: t('report.totalClients'), value: formatNumber(report.totalClients)},
                {
                    key: 'failedClients',
                    label: t('report.failedClients'),
                    value: formatNumber(report.failedClients),
                    tone: report.failedClients ? 'danger' : 'success',
                },
                {key: 'totalNodes', label: t('report.totalNodes'), value: formatNumber(report.totalNodes)},
                {key: 'onlineNodes', label: t('report.onlineNodes'), value: formatNumber(report.onlineNodes)},
            ],
        },
        ...(!isConnReport ? [
            {
                key: 'messages',
                title: t('report.messageMetrics'),
                items: [
                    {key: 'sent', label: t('report.totalMsgSent'), value: formatNumber(report.totalMessagesSent)},
                    {key: 'received', label: t('report.totalMsgReceived'), value: formatNumber(report.totalMessagesReceived)},
                    {key: 'publishQps', label: t('report.publishQps'), value: formatRate(report.avgPublishQps)},
                    {key: 'receiveQps', label: t('report.receiveQps'), value: formatRate(report.avgReceiveQps)},
                    {key: 'bytes', label: t('report.totalBytesTransmitted'), value: formatNumber(report.totalBytesTransmitted)},
                    {key: 'avgBytes', label: t('report.avgThroughputBytes'), value: `${formatFixed(report.avgBytesPerSecond)} B/s`},
                    {key: 'pubackLatency', label: t('report.pubackLatencyP95'), value: formatMs(report.pubackLatencyP95)},
                ],
            } satisfies MetricGroup,
        ] : []),
        ...(isPubsubReport ? [
            {
                key: 'quality',
                title: t('report.qualityMetrics'),
                items: [
                    {key: 'latencyP50', label: t('report.latencyP50'), value: formatMs(report.latencyP50)},
                    {key: 'latencyP95', label: t('report.latencyP95Label'), value: formatMs(report.latencyP95)},
                    {key: 'latencyP99', label: t('report.latencyP99Label'), value: formatMs(report.latencyP99)},
                    {key: 'latencyMax', label: t('report.latencyMax'), value: formatMs(report.latencyMax)},
                    {
                        key: 'duplicateMsg',
                        label: t('report.duplicateMsg'),
                        value: formatNumber(report.totalDuplicateMessages),
                        tone: report.totalDuplicateMessages ? 'warning' : 'default',
                    },
                    {key: 'duplicateRate', label: t('report.duplicateRate'), value: formatPercent(report.duplicateRate)},
                    {
                        key: 'loss',
                        label: t('report.estimatedLoss'),
                        value: formatNumber(report.estimatedMessageLoss),
                        tone: report.estimatedMessageLoss ? 'danger' : 'success',
                    },
                    {key: 'lossRate', label: t('report.lossRate'), value: formatPercent(report.messageLossRate)},
                ],
            } satisfies MetricGroup,
        ] : []),
    ];

    const nodeColumns = [
        {
            title: t('task.detail.reportPanel.node'), dataIndex: 'nodeName', key: 'nodeName', width: 120,
            render: (name: string, r: NodeReport) => (
                <div className="task-report-node-name">
                    <div className="task-report-node-title">{name || r.nodeId}</div>
                    <div className="task-report-node-id">{r.nodeId}</div>
                </div>
            )
        },
        {
            title: t('task.detail.reportPanel.assignedClients'), dataIndex: 'assignedClients', key: 'assignedClients', width: 90,
            render: (v?: number) => v?.toLocaleString() || '-'
        },
        {
            title: t('task.detail.reportPanel.connSuccess'), dataIndex: 'connectSuccess', key: 'connectSuccess', width: 90,
            render: (v?: number) => v?.toLocaleString() || '-'
        },
        {
            title: t('task.detail.reportPanel.connFail'), dataIndex: 'connectFailure', key: 'connectFailure', width: 90,
            render: (v?: number) => (v != null && v > 0) ? <Tag color="red">{v.toLocaleString()}</Tag> :
                <span className="task-report-muted">-</span>
        },
        {
            title: t('report.connQpsColumn'), dataIndex: 'avgConnectQps', key: 'avgConnectQps', width: 90,
            render: (v?: number) => formatRate(v)
        },
        ...(!isConnReport ? [{
            title: t('task.detail.reportPanel.published'), dataIndex: 'messagesSent', key: 'messagesSent', width: 90,
            render: (v?: number) => v?.toLocaleString() || '-'
        },
        {
            title: t('report.publishQpsColumn'), dataIndex: 'avgPublishQps', key: 'avgPublishQps', width: 90,
            render: (v?: number) => formatRate(v)
        },
        {
            title: t('task.detail.reportPanel.received'), dataIndex: 'messagesReceived', key: 'messagesReceived', width: 90,
            render: (v?: number) => v?.toLocaleString() || '-'
        },
        {
            title: t('report.receiveQpsColumn'), dataIndex: 'avgReceiveQps', key: 'avgReceiveQps', width: 90,
            render: (v?: number) => formatRate(v)
        }] : []),
        ...(isPubsubReport ? [{
            title: t('report.nodeLatencyP95'), dataIndex: 'latencyP95', key: 'latencyP95', width: 100,
            render: (v?: number) => formatMs(v)
        }] : []),
    ];

    return (
        <div className="task-report-panel">
            <section className="task-report-hero">
                <div className="task-report-hero-main">
                    <Space size={8} wrap>
                        <Tag color="blue">{reportKind}</Tag>
                        <Tag color={reportHealthColor}>{reportHealthText}</Tag>
                    </Space>
                    <Typography.Title level={4} className="task-report-title">
                        {report.taskName || t('report.unnamedTask')}
                    </Typography.Title>
                    <div className="task-report-meta">
                        <span>{t('report.taskId')}: {report.taskId}</span>
                        <span>{t('report.totalNodes')}: {formatNumber(report.totalNodes)}</span>
                        <span>{t('report.totalClients')}: {formatNumber(report.totalClients)}</span>
                    </div>
                </div>
                <div className="task-report-hero-metrics">
                    {primaryMetrics.map((metric) => (
                        <div className={`task-report-metric task-report-metric-${metric.tone || 'default'}`}
                             key={metric.key}>
                            <div className="task-report-metric-label">{metric.label}</div>
                            <div className="task-report-metric-value">{metric.value}</div>
                        </div>
                    ))}
                </div>
            </section>

            <div className="task-report-section-grid">
                {metricGroups.map((group) => (
                    <section className="task-report-section" key={group.key}>
                        <div className="task-report-section-title">{group.title}</div>
                        <div className="task-report-detail-grid">
                            {group.items.map((metric) => (
                                <div className={`task-report-detail-item task-report-detail-${metric.tone || 'default'}`}
                                     key={metric.key}>
                                    <span>{metric.label}</span>
                                    <strong>{metric.value}</strong>
                                </div>
                            ))}
                        </div>
                    </section>
                ))}
            </div>

            {/* QoS distribution */}
            {!isConnReport && report.qosDistribution && (
                <section className="task-report-section">
                    <div className="task-report-section-title">{t('report.qosDistribution')}</div>
                    <div className="task-report-qos-grid">
                        {[
                            {
                                key: 'qos0',
                                label: t('task.form.qos0'),
                                count: report.qosDistribution.qos0Count,
                                percent: report.qosDistribution.qos0Percent,
                                color: '#00b173',
                            },
                            {
                                key: 'qos1',
                                label: t('task.form.qos1'),
                                count: report.qosDistribution.qos1Count,
                                percent: report.qosDistribution.qos1Percent,
                                color: '#1677ff',
                            },
                            {
                                key: 'qos2',
                                label: t('task.form.qos2'),
                                count: report.qosDistribution.qos2Count,
                                percent: report.qosDistribution.qos2Percent,
                                color: '#722ed1',
                            },
                        ].map((qos) => (
                            <div className="task-report-qos-item" key={qos.key}>
                                <div className="task-report-qos-row">
                                    <span>{qos.label}</span>
                                    <strong>{formatNumber(qos.count)} · {formatPercent(qos.percent, 1)}</strong>
                                </div>
                                <Progress percent={qos.percent || 0} showInfo={false} strokeColor={qos.color}/>
                            </div>
                        ))}
                    </div>
                </section>
            )}

            {/* Exception statistics */}
            {report.errorCounts && Object.keys(report.errorCounts).length > 0 && (
                <section className="task-report-section">
                    <div className="task-report-section-title">{t('report.errorStats')}</div>
                    <div className="task-report-tags">
                        {Object.entries(report.errorCounts).map(([key, value]) => (
                            <span className="task-report-error-item" key={key}>
                                <span>{key}</span>
                                <Tag color="red">{value.toLocaleString()}</Tag>
                            </span>
                        ))}
                    </div>
                </section>
            )}

            {/* Node details */}
            {report.nodeReports && report.nodeReports.length > 0 && (
                <section className="task-report-section">
                    <div className="task-report-section-title">{t('report.nodeDetail')}</div>
                    <Table<NodeReport>
                        dataSource={report.nodeReports}
                        columns={nodeColumns}
                        rowKey="nodeId"
                        pagination={false}
                        size="small"
                    />
                </section>
            )}

            {/* Chaos test results */}
            {report.chaosResults && Object.keys(report.chaosResults).length > 0 && (
                <section className="task-report-section">
                    <div className="task-report-section-title">{t('report.chaosResult')}</div>
                    <div className="task-report-chaos-list">
                        {Object.entries(report.chaosResults).map(([behavior, reactions]) => (
                            <div className="task-report-chaos-item" key={behavior}>
                                <Tag color="volcano">{behavior}</Tag>
                                <div>
                                    {Object.entries(reactions).map(([reaction, count]) => (
                                        <Tag key={reaction} color="orange">
                                            {reaction}: {count.toLocaleString()}
                                        </Tag>
                                    ))}
                                </div>
                            </div>
                        ))}
                    </div>
                </section>
            )}
        </div>
    );
};

export default TaskReportPanel;
