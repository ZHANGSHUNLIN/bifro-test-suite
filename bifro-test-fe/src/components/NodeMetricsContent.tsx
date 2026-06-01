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
import {Table, Tag} from 'antd';
import {useTranslation} from 'react-i18next';
import {getCounterMetricLabel, getTimerMetricLabel} from '../utils/taskUtils';
import type {CounterMetricData, TimerMetricData} from '../features/task';

interface NodeMetricsContentProps {
    counterMetrics?: CounterMetricData[];
    timerMetrics?: TimerMetricData[];
    compact?: boolean;
}

type MetricTone = 'default' | 'success' | 'warning' | 'danger' | 'accent';

type SummaryMetric = {
    key: string;
    label: string;
    value: React.ReactNode;
    tone?: MetricTone;
};

const formatTags = (tags: Record<string, string>): string => {
    return Object.entries(tags)
        .map(([k, v]) => `${k}=${v}`)
        .join(', ');
};

const formatCount = (value?: number) => value != null ? value.toLocaleString() : '-';

const formatDuration = (valueMs?: number, hasData = true): string => {
    if (valueMs == null || !hasData) {
        return '-';
    }
    if (valueMs < 1) {
        return `${Math.round(valueMs * 1_000_000).toLocaleString()} ns`;
    }
    if (valueMs < 1000) {
        return `${valueMs.toFixed(2)} ms`;
    }
    return `${(valueMs / 1000).toFixed(2)} s`;
};

const NodeMetricsContent: React.FC<NodeMetricsContentProps> = ({
                                                                   counterMetrics = [],
                                                                   timerMetrics = [],
                                                                   compact = false
                                                               }) => {
    const {t} = useTranslation();
    const visibleTimers = timerMetrics.filter(metric => metric.hasData);
    const totalCalls = timerMetrics.reduce((sum, metric) => sum + metric.count, 0);
    const totalCounters = counterMetrics.reduce((sum, metric) => sum + metric.count, 0);
    const maxLatency = visibleTimers.reduce((max, metric) => Math.max(max, metric.max || 0), 0);

    const summaryMetrics: SummaryMetric[] = [
        {
            key: 'timers',
            label: t('metrics.timer'),
            value: formatCount(timerMetrics.length),
            tone: 'accent',
        },
        {
            key: 'timerCalls',
            label: t('metrics.totalTimerCalls'),
            value: formatCount(totalCalls),
            tone: 'success',
        },
        {
            key: 'counters',
            label: t('metrics.counter'),
            value: formatCount(counterMetrics.length),
        },
        {
            key: 'counterValues',
            label: t('metrics.totalCounterValues'),
            value: formatCount(totalCounters),
        },
        {
            key: 'maxLatency',
            label: t('metrics.maxDuration'),
            value: visibleTimers.length > 0 ? formatDuration(maxLatency) : '-',
            tone: maxLatency > 0 ? 'warning' : 'default',
        },
    ];

    const counterColumns = [
        {
            title: t('metrics.columns.metricName'),
            dataIndex: 'name',
            key: 'name',
            width: 220,
            render: (name: string) => <strong>{getCounterMetricLabel(name)}</strong>,
        },
        {
            title: t('metrics.columns.labels'),
            dataIndex: 'tags',
            key: 'tags',
            render: (tags: Record<string, string>) => (
                <span className="node-metrics-tags">{formatTags(tags)}</span>
            ),
        },
        {
            title: t('metrics.columns.countValue'),
            dataIndex: 'count',
            key: 'count',
            width: 120,
            align: 'right' as const,
            render: (count: number) => <strong>{formatCount(count)}</strong>,
        },
    ];

    const timerColumns = [
        {
            title: t('metrics.columns.metricName'),
            dataIndex: 'name',
            key: 'name',
            width: 200,
            render: (name: string, record: TimerMetricData) => (
                <div>
                    <strong>{getTimerMetricLabel(name)}</strong>
                    {!record.hasData && <Tag color="default" style={{marginLeft: 6}}>{t('metrics.noSamples')}</Tag>}
                </div>
            ),
        },
        {
            title: t('metrics.columns.labels'),
            dataIndex: 'tags',
            key: 'tags',
            width: 260,
            render: (tags: Record<string, string>) => (
                <span className="node-metrics-tags">{formatTags(tags)}</span>
            ),
        },
        {
            title: t('metrics.columns.count'),
            dataIndex: 'count',
            key: 'count',
            width: 100,
            align: 'right' as const,
            render: (count: number) => formatCount(count),
        },
        {
            title: t('metrics.avgDuration'),
            dataIndex: 'mean',
            key: 'mean',
            width: 130,
            align: 'right' as const,
            render: (mean: number, record: TimerMetricData) => formatDuration(mean, record.hasData),
        },
        {
            title: t('metrics.columns.p50Duration'),
            dataIndex: 'p50',
            key: 'p50',
            width: 130,
            align: 'right' as const,
            render: (p50: number, record: TimerMetricData) => formatDuration(p50, record.hasData),
        },
        {
            title: t('metrics.p95Duration'),
            dataIndex: 'p95',
            key: 'p95',
            width: 130,
            align: 'right' as const,
            render: (p95: number, record: TimerMetricData) => formatDuration(p95, record.hasData),
        },
        {
            title: t('metrics.columns.p99Duration'),
            dataIndex: 'p99',
            key: 'p99',
            width: 130,
            align: 'right' as const,
            render: (p99: number, record: TimerMetricData) => formatDuration(p99, record.hasData),
        },
        {
            title: t('metrics.maxDuration'),
            dataIndex: 'max',
            key: 'max',
            width: 130,
            align: 'right' as const,
            render: (max: number, record: TimerMetricData) => formatDuration(max, record.hasData),
        },
    ];

    return (
        <div className={`task-report-panel node-metrics-panel${compact ? ' node-metrics-panel-compact' : ''}`}>
            <section className="task-report-hero node-metrics-hero">
                <div className="task-report-hero-main">
                    <Tag color="blue">{t('metrics.nodeMetrics')}</Tag>
                    <div className="task-report-title ant-typography">{t('metrics.snapshotTitle')}</div>
                    <div className="task-report-meta">
                        <span>{t('metrics.timeUnitAuto')}</span>
                    </div>
                </div>
                <div className="task-report-hero-metrics">
                    {summaryMetrics.map(metric => (
                        <div
                            className={`task-report-metric task-report-metric-${metric.tone || 'default'}`}
                            key={metric.key}
                        >
                            <div className="task-report-metric-label">{metric.label}</div>
                            <div className="task-report-metric-value">{metric.value}</div>
                        </div>
                    ))}
                </div>
            </section>

            {timerMetrics.length > 0 && (
                <section className="task-report-section">
                    <div className="task-report-section-title">{t('metrics.timer')}</div>
                    <Table<TimerMetricData>
                        dataSource={timerMetrics}
                        columns={timerColumns}
                        rowKey={(record) => `${record.name}-${formatTags(record.tags)}`}
                        pagination={false}
                        size="small"
                        scroll={{x: 1230}}
                    />
                </section>
            )}

            {counterMetrics.length > 0 && (
                <section className="task-report-section">
                    <div className="task-report-section-title">{t('metrics.counter')}</div>
                    <Table<CounterMetricData>
                        dataSource={counterMetrics}
                        columns={counterColumns}
                        rowKey={(record) => `${record.name}-${formatTags(record.tags)}`}
                        pagination={false}
                        size="small"
                        scroll={{x: 700}}
                    />
                </section>
            )}
        </div>
    );
};

export default NodeMetricsContent;
