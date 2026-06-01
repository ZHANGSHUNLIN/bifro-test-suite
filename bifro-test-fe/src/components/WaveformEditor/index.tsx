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


import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Alert, Button, Col, Input, InputNumber, Popconfirm, Row, Space, Table, Tag, Tooltip, Typography,} from 'antd';
import {DeleteOutlined, PlusOutlined} from '@ant-design/icons';
import {CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip as RechartsTooltip, XAxis, YAxis,} from 'recharts';
import {useTranslation} from 'react-i18next';

// ─────────────────────────────────────────────────────────────
// Public API types
// ─────────────────────────────────────────────────────────────

export interface ControlPoint {
    
    key: number;
    
    ms: number;
    
    qps: number;
}

export interface WaveformEditorValue {
    
    totalDurationMs: number;
    
    dataPoints: number[][];
    
    maxQps: number;
    
    targetTotalCount?: number;
}

interface WaveformEditorProps {
    value: WaveformEditorValue;
    onChange: (v: WaveformEditorValue) => void;
}

// ─────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────

let keyCounter = 100;

function nextKey() {
    return ++keyCounter;
}

function fmtMs(ms: number): string {
    const totalSec = Math.round(ms / 1000);
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    const s = totalSec % 60;
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    return `${m}:${String(s).padStart(2, '0')}`;
}

function parseTimeInput(raw: string): number | null {
    const parts = raw.trim().split(':');
    if (parts.length === 1) {
        const sec = Number(parts[0]);
        return isNaN(sec) ? null : sec * 1000;
    }
    if (parts.length === 2) {
        const [mm, ss] = parts.map(Number);
        if (isNaN(mm) || isNaN(ss)) return null;
        return (mm * 60 + ss) * 1000;
    }
    if (parts.length === 3) {
        const [hh, mm, ss] = parts.map(Number);
        if (isNaN(hh) || isNaN(mm) || isNaN(ss)) return null;
        return (hh * 3600 + mm * 60 + ss) * 1000;
    }
    return null;
}

function gridMs(totalDurationMs: number): number {
    const minutes = totalDurationMs / 60_000;
    if (minutes < 10) return 10_000;
    if (minutes <= 60) return 60_000;
    return 300_000;
}

function snapMs(ms: number, totalDurationMs: number): number {
    const g = gridMs(totalDurationMs);
    return Math.round(ms / g) * g;
}

function calcIntegral(pts: ControlPoint[]): number {
    let sum = 0;
    for (let i = 0; i < pts.length - 1; i++) {
        sum += (pts[i].qps + pts[i + 1].qps) / 2 * (pts[i + 1].ms - pts[i].ms) / 1000;
    }
    return Math.round(sum);
}

function toDataPoints(pts: ControlPoint[]): number[][] {
    return pts.map(p => [p.ms, p.qps]);
}

function fromDataPoints(dp: number[][], totalDurationMs: number): ControlPoint[] {
    if (!dp || dp.length < 2) {
        return [
            {key: 0, ms: 0, qps: 0},
            {key: 1, ms: totalDurationMs, qps: 0},
        ];
    }
    return dp.map((p, i) => ({key: i, ms: p[0], qps: p[1]}));
}

// ─────────────────────────────────────────────────────────────
// Segment slope summary row
// ─────────────────────────────────────────────────────────────

interface SlopeRow {
    from: string;
    to: string;
    slope: number;
    dir: 'up' | 'flat' | 'down';
}

function buildSlopeRows(pts: ControlPoint[]): SlopeRow[] {
    const rows: SlopeRow[] = [];
    for (let i = 0; i < pts.length - 1; i++) {
        const durationSec = (pts[i + 1].ms - pts[i].ms) / 1000;
        if (durationSec <= 0) continue;
        const slope = (pts[i + 1].qps - pts[i].qps) / durationSec;
        const dir: SlopeRow['dir'] = slope > 0.1 ? 'up' : slope < -0.1 ? 'down' : 'flat';
        rows.push({
            from: fmtMs(pts[i].ms),
            to: fmtMs(pts[i + 1].ms),
            slope,
            dir,
        });
    }
    return rows;
}

// ─────────────────────────────────────────────────────────────
// Custom dot — draggable control point rendered by recharts
// ─────────────────────────────────────────────────────────────

interface DotProps {
    cx?: number;
    cy?: number;
    index?: number;
    payload?: { ms: number; qps: number; key: number };
    isFirst?: boolean;
    isLast?: boolean;
    onMouseDown?: (index: number, cx: number, cy: number) => void;
}

function ControlDot({cx = 0, cy = 0, index = 0, isFirst, isLast, onMouseDown}: DotProps) {
    const isEndpoint = isFirst || isLast;
    const color = isEndpoint ? '#ff7043' : '#4096ff';
    const size = isEndpoint ? 7 : 8;

    return (
        <circle
            cx={cx}
            cy={cy}
            r={size}
            fill={color}
            stroke="#fff"
            strokeWidth={2.5}
            style={{cursor: isEndpoint ? 'ns-resize' : 'grab'}}
            onMouseDown={e => {
                e.stopPropagation();
                onMouseDown?.(index ?? 0, cx, cy);
            }}
        />
    );
}

// ─────────────────────────────────────────────────────────────
// Main component
// ─────────────────────────────────────────────────────────────

export function WaveformEditor({value, onChange}: WaveformEditorProps) {
    const {t} = useTranslation();
    const {totalDurationMs, maxQps, targetTotalCount} = value;

    // ── internal control-point state ─────────────────────────
    const [pts, setPts] = useState<ControlPoint[]>(() =>
        fromDataPoints(value.dataPoints, totalDurationMs),
    );

    // Keep pts in sync when parent replaces value (e.g. step navigation).
    const prevDpRef = useRef(value.dataPoints);
    useEffect(() => {
        if (value.dataPoints !== prevDpRef.current) {
            prevDpRef.current = value.dataPoints;
            setPts(fromDataPoints(value.dataPoints, totalDurationMs));
        }
    }, [value.dataPoints, totalDurationMs]);

    // ── normalization error state ─────────────────────────────
    const [normalizeError, setNormalizeError] = useState<string | null>(null);

    // ── drag state (refs to avoid re-renders during drag) ────
    const dragRef = useRef<{
        idx: number;
        startPx: { x: number; y: number };
        startPt: { ms: number; qps: number };
        svgRect: DOMRect;
        chartMeta: { xMin: number; xMax: number; yMin: number; yMax: number; w: number; h: number };
    } | null>(null);

    const chartContainerRef = useRef<HTMLDivElement>(null);

    // ── derived chart data ───────────────────────────────────
    const chartData = useMemo(
        () => pts.map(p => ({ms: p.ms, qps: p.qps, key: p.key})),
        [pts],
    );

    // ── commit helper: update pts + notify parent ────────────
    const commit = useCallback((newPts: ControlPoint[]) => {
        const sorted = [...newPts].sort((a, b) => a.ms - b.ms);
        setPts(sorted);
        setNormalizeError(null);
        onChange({...value, dataPoints: toDataPoints(sorted)});
    }, [onChange, value]);

    // ── param change helpers ─────────────────────────────────
    const handleMaxQpsChange = useCallback((v: number | null) => {
        if (!v || v <= 0) return;
        // Clamp existing pts
        const newPts = pts.map(p => ({...p, qps: Math.min(p.qps, v)}));
        setPts(newPts);
        onChange({...value, maxQps: v, dataPoints: toDataPoints(newPts)});
    }, [value, pts, onChange]);

    const handleDurationChange = useCallback((rawInput: string) => {
        const ms = parseTimeInput(rawInput);
        if (!ms || ms <= 0) return;
        // Re-anchor last endpoint to new duration
        const newPts: ControlPoint[] = pts.map((p, i) =>
            i === pts.length - 1 ? {...p, ms} : p,
        ).filter((p, i) => i === pts.length - 1 || p.ms < ms);
        setPts(newPts);
        onChange({...value, totalDurationMs: ms, dataPoints: toDataPoints(newPts)});
    }, [value, pts, onChange]);

    const handleTargetTotalCountChange = useCallback((v: number | null) => {
        setNormalizeError(null);
        onChange({...value, targetTotalCount: v ?? undefined});
    }, [value, onChange]);

    // ── chart coordinate conversion ──────────────────────────
    const getChartMeta = useCallback(() => {
        if (!chartContainerRef.current) return null;
        const MARGIN = {top: 10, right: 20, bottom: 40, left: 60};
        const rect = chartContainerRef.current.getBoundingClientRect();
        const w = rect.width - MARGIN.left - MARGIN.right;
        const h = rect.height - MARGIN.top - MARGIN.bottom;
        return {xMin: 0, xMax: totalDurationMs, yMin: 0, yMax: maxQps, w, h, margin: MARGIN, rect};
    }, [totalDurationMs, maxQps]);

    const pxToData = useCallback((clientX: number, clientY: number) => {
        const meta = getChartMeta();
        if (!meta) return null;
        const {rect, margin, w, h, xMax, yMax: ym} = meta;
        const relX = clientX - rect.left - margin.left;
        const relY = clientY - rect.top - margin.top;
        const ms = (relX / w) * xMax;
        const qps = ym - (relY / h) * ym;
        return {ms, qps};
    }, [getChartMeta]);

    // ── drag handlers ────────────────────────────────────────
    const handleDotMouseDown = useCallback((idx: number, _cx: number, _cy: number) => {
        if (!chartContainerRef.current) return;
        const rect = chartContainerRef.current.getBoundingClientRect();
        const meta = getChartMeta();
        if (!meta) return;
        dragRef.current = {
            idx,
            startPx: {x: 0, y: 0},
            startPt: {ms: pts[idx].ms, qps: pts[idx].qps},
            svgRect: rect,
            chartMeta: meta,
        };
    }, [pts, getChartMeta]);

    const handleMouseMove = useCallback((e: React.MouseEvent) => {
        if (!dragRef.current) return;
        const {idx} = dragRef.current;
        const result = pxToData(e.clientX, e.clientY);
        if (!result) return;

        const isFirst = idx === 0;
        const isLast = idx === pts.length - 1;

        const newQps = Math.max(0, Math.min(maxQps, Math.round(result.qps)));
        let newMs = pts[idx].ms;

        if (!isFirst && !isLast) {
            const g = gridMs(totalDurationMs);
            let snapped = snapMs(result.ms, totalDurationMs);
            snapped = Math.max(pts[idx - 1].ms + g, Math.min(pts[idx + 1].ms - g, snapped));
            newMs = snapped;
        }

        setPts(prev => prev.map((p, i) => i === idx ? {...p, ms: newMs, qps: newQps} : p));
    }, [pts, pxToData, totalDurationMs, maxQps]);

    const handleMouseUp = useCallback(() => {
        if (!dragRef.current) return;
        dragRef.current = null;
        setPts(prev => {
            const sorted = [...prev].sort((a, b) => a.ms - b.ms);
            onChange({...value, dataPoints: toDataPoints(sorted)});
            return sorted;
        });
    }, [onChange, value]);

    // Add point on click on chart background
    const handleChartClick = useCallback((e: React.MouseEvent) => {
        if (dragRef.current) return;
        const result = pxToData(e.clientX, e.clientY);
        if (!result) return;

        const g = gridMs(totalDurationMs);
        const snappedMs = Math.max(g, Math.min(totalDurationMs - g, snapMs(result.ms, totalDurationMs)));
        if (pts.some(p => p.ms === snappedMs)) return;
        const newQps = Math.max(0, Math.min(maxQps, Math.round(result.qps)));

        commit([...pts, {key: nextKey(), ms: snappedMs, qps: newQps}]);
    }, [pts, pxToData, totalDurationMs, maxQps, commit]);

    // ── table operations ─────────────────────────────────────
    const handleTableTimeChange = useCallback((key: number, rawTime: string) => {
        const ms = parseTimeInput(rawTime);
        if (ms === null) return;
        if (ms < 0 || ms > totalDurationMs) return;
        if (pts.some(p => p.key !== key && p.ms === ms)) return;
        const idx = pts.findIndex(p => p.key === key);
        if (idx === 0 || idx === pts.length - 1) return; // protect endpoints
        commit(pts.map(p => p.key === key ? {...p, ms} : p));
    }, [pts, totalDurationMs, commit]);

    const handleTableQpsChange = useCallback((key: number, qps: number | null) => {
        if (qps === null) return;
        const idx = pts.findIndex(p => p.key === key);
        if (idx < 0) return;
        commit(pts.map(p => p.key === key ? {...p, qps: Math.max(0, Math.min(maxQps, qps))} : p));
    }, [pts, maxQps, commit]);

    const handleDeleteRow = useCallback((key: number) => {
        const idx = pts.findIndex(p => p.key === key);
        if (idx <= 0 || idx >= pts.length - 1) return; // protect endpoints
        commit(pts.filter(p => p.key !== key));
    }, [pts, commit]);

    const handleAddRow = useCallback(() => {
        const lastInner = pts[pts.length - 2];
        const endpoint = pts[pts.length - 1];
        const g = gridMs(totalDurationMs);
        const newMs = Math.min(lastInner.ms + g, endpoint.ms - g);
        if (pts.some(p => p.ms === newMs)) return;
        commit([...pts, {key: nextKey(), ms: newMs, qps: 0}]);
    }, [pts, totalDurationMs, commit]);

    // ── derived stats ─────────────────────────────────────────
    const integral = useMemo(() => calcIntegral(pts), [pts]);
    const peakQps = Math.max(...pts.map(p => p.qps));
    const avgQps = totalDurationMs > 0
        ? (integral / (totalDurationMs / 1000)).toFixed(1)
        : '0.0';

    // Pre-validation: is targetTotalCount mathematically reachable?
    const maxReachableIntegral = maxQps * (totalDurationMs / 1000);
    const targetUnreachable = !!targetTotalCount && targetTotalCount > maxReachableIntegral;

    // ── normalization ─────────────────────────────────────────
    const handleNormalize = useCallback(() => {
        if (!targetTotalCount || integral === 0) return;
        const scale = targetTotalCount / integral;
        const normalizedPts = pts.map((p, i, arr) =>
            i === 0 || i === arr.length - 1
                ? {...p, qps: 0}
                : {...p, qps: Math.round(p.qps * scale)},
        );
        const normalizedPeak = Math.max(...normalizedPts.map(p => p.qps));
        if (normalizedPeak > maxQps) {
            setNormalizeError(
                t('profile.waveform.peakExceeded', {peak: normalizedPeak.toLocaleString(), maxQps: maxQps.toLocaleString()}),
            );
            return;
        }
        setNormalizeError(null);
        commit(normalizedPts);
    }, [pts, integral, targetTotalCount, maxQps, commit]);

    const slopeRows = useMemo(() => buildSlopeRows(pts), [pts]);

    // ── table columns ─────────────────────────────────────────
    const columns = [
        {
            title: t('profile.waveform.columns.time'),
            dataIndex: 'ms',
            key: 'ms',
            width: 100,
            render: (ms: number, record: ControlPoint) => {
                const isEndpoint = record.key === pts[0].key || record.key === pts[pts.length - 1].key;
                return isEndpoint
                    ? <Typography.Text type="secondary">{fmtMs(ms)}</Typography.Text>
                    : (
                        <Input
                            defaultValue={fmtMs(ms)}
                            key={`t-${record.key}-${ms}`}
                            size="small"
                            style={{width: 80}}
                            onPressEnter={e => handleTableTimeChange(record.key, e.currentTarget.value)}
                            onBlur={e => handleTableTimeChange(record.key, e.target.value)}
                        />
                    );
            },
        },
        {
            title: t('profile.waveform.columns.qps'),
            dataIndex: 'qps',
            key: 'qps',
            width: 110,
            render: (qps: number, record: ControlPoint) => {
                const isFirst = record.key === pts[0].key;
                const isLast = record.key === pts[pts.length - 1].key;
                return (isFirst || isLast)
                    ? <Typography.Text type="secondary">{qps}</Typography.Text>
                    : (
                        <InputNumber
                            value={qps}
                            min={0}
                            max={maxQps}
                            size="small"
                            style={{width: 90}}
                            onChange={v => handleTableQpsChange(record.key, v)}
                        />
                    );
            },
        },
        {
            title: t('profile.waveform.columns.actions'),
            key: 'action',
            width: 60,
            render: (_: unknown, record: ControlPoint) => {
                const isFirst = record.key === pts[0].key;
                const isLast = record.key === pts[pts.length - 1].key;
                if (isFirst || isLast) return <Typography.Text type="secondary">—</Typography.Text>;
                return (
                    <Popconfirm title={t('profile.waveform.deletePoint')} onConfirm={() => handleDeleteRow(record.key)}>
                        <Button type="text" danger size="small" icon={<DeleteOutlined/>}/>
                    </Popconfirm>
                );
            },
        },
    ];

    // ── custom recharts dot renderer ──────────────────────────
    const renderDot = useCallback((dotProps: DotProps) => {
        const {index = 0} = dotProps;
        return (
            <ControlDot
                key={`dot-${index}`}
                {...dotProps}
                isFirst={index === 0}
                isLast={index === pts.length - 1}
                onMouseDown={handleDotMouseDown}
            />
        );
    }, [pts.length, handleDotMouseDown]);

    // ── scale preview for normalize button tooltip ────────────
    const scalePreview = targetTotalCount && integral > 0
        ? (targetTotalCount / integral).toFixed(3)
        : null;

    return (
        <div style={{userSelect: 'none'}}>
            {/* ─── Param row ─────────────────────────────────── */}
            <Row gutter={16} style={{marginBottom: 14}} align="middle">
                <Col>
                    <Space size={4}>
                        <Typography.Text type="secondary" style={{fontSize: 12}}>{t('profile.waveform.maxQpsLabel')}</Typography.Text>
                        <Tooltip title={t('profile.waveform.maxQpsTooltip')}>
                            <InputNumber
                                value={maxQps}
                                min={1}
                                max={1_000_000}
                                step={100}
                                style={{width: 110}}
                                addonAfter={t('profile.waveform.addonAfterQps')}
                                onChange={handleMaxQpsChange}
                            />
                        </Tooltip>
                    </Space>
                </Col>
                <Col>
                    <Space size={4}>
                        <Typography.Text type="secondary" style={{fontSize: 12}}>{t('profile.waveform.durationLabel')}</Typography.Text>
                        <Tooltip title={t('profile.waveform.durationTooltip')}>
                            <Input
                                defaultValue={fmtMs(totalDurationMs)}
                                key={`dur-${totalDurationMs}`}
                                style={{width: 110}}
                                placeholder={t('profile.waveform.durationPlaceholder')}
                                onPressEnter={e => handleDurationChange(e.currentTarget.value)}
                                onBlur={e => handleDurationChange(e.target.value)}
                            />
                        </Tooltip>
                    </Space>
                </Col>
                <Col>
                    <Space size={4}>
                        <Typography.Text type="secondary" style={{fontSize: 12}}>{t('profile.waveform.targetCountLabel')}</Typography.Text>
                        <Tooltip title={t('profile.waveform.targetCountTooltip')}>
                            <InputNumber
                                value={targetTotalCount ?? null}
                                min={1}
                                style={{width: 130}}
                                addonAfter={t('profile.waveform.addonAfterCount')}
                                placeholder={t('profile.waveform.targetCountPlaceholder')}
                                status={targetUnreachable ? 'error' : undefined}
                                onChange={handleTargetTotalCountChange}
                            />
                        </Tooltip>
                    </Space>
                </Col>
                {targetUnreachable && (
                    <Col>
                        <Typography.Text type="danger" style={{fontSize: 12}}>
                            {t('profile.waveform.targetUnreachable', {max: Math.floor(maxReachableIntegral).toLocaleString(), qps: maxQps, sec: (totalDurationMs / 1000).toFixed(0)})}
                        </Typography.Text>
                    </Col>
                )}
            </Row>

            <Row gutter={16}>
                {/* ─── Left: Canvas ──────────────────────────────── */}
                <Col flex="58%">
                    <div
                        ref={chartContainerRef}
                        style={{position: 'relative', height: 460, cursor: 'crosshair'}}
                        onMouseMove={handleMouseMove}
                        onMouseUp={handleMouseUp}
                        onMouseLeave={handleMouseUp}
                        onClick={handleChartClick}
                    >
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart
                                data={chartData}
                                margin={{top: 10, right: 20, bottom: 40, left: 60}}
                            >
                                <CartesianGrid strokeDasharray="3 3" stroke="#f5f5f5"/>
                                <XAxis
                                    dataKey="ms"
                                    type="number"
                                    domain={[0, totalDurationMs]}
                                    tickFormatter={fmtMs}
                                    label={{value: t('profile.waveform.timeAxisLabel'), position: 'insideBottomRight', offset: -10}}
                                    tick={{fontSize: 11}}
                                />
                                <YAxis
                                    domain={[0, maxQps]}
                                    label={{value: t('profile.waveform.columns.qps'), angle: -90, position: 'insideLeft', offset: 10}}
                                    tick={{fontSize: 11}}
                                />
                                <RechartsTooltip
                                    formatter={(v: number) => [t('profile.waveform.tooltipQps', {v}), t('profile.waveform.columns.qps')]}
                                    labelFormatter={fmtMs}
                                />
                                <Line
                                    type="linear"
                                    dataKey="qps"
                                    stroke="#4096ff"
                                    strokeWidth={2.5}
                                    dot={renderDot as never}
                                    activeDot={false}
                                    isAnimationActive={false}
                                />
                            </LineChart>
                        </ResponsiveContainer>
                    </div>

                    {/* ── Status bar with prominent integral ── */}
                    <div style={{
                        display: 'flex', gap: 20, padding: '10px 14px',
                        background: '#f6f8ff', border: '1px solid #d6e4ff',
                        borderRadius: 8, marginTop: 8, alignItems: 'center',
                        flexWrap: 'wrap',
                    }}>
                        <div style={{textAlign: 'center'}}>
                            <div style={{fontSize: 22, fontWeight: 700, color: '#1677ff', lineHeight: 1.2}}>
                                {integral.toLocaleString()}
                            </div>
                            <div style={{fontSize: 11, color: '#888'}}>{t('profile.waveform.currentIntegral')}</div>
                        </div>
                        <div style={{width: 1, height: 36, background: '#d6e4ff'}}/>
                        <div style={{fontSize: 12, color: '#555', display: 'flex', flexDirection: 'column', gap: 2}}>
                            <span>{t('profile.waveform.controlPoints', {n: pts.length})}</span>
                            <span>{t('profile.waveform.peakQpsValue', {n: peakQps})}</span>
                            <span>{t('profile.waveform.avgQpsValue', {n: avgQps})}</span>
                        </div>
                        {targetTotalCount && !targetUnreachable && (
                            <>
                                <div style={{width: 1, height: 36, background: '#d6e4ff'}}/>
                                <div style={{
                                    fontSize: 12,
                                    color: '#555',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: 2
                                }}>
                                    <span>{t('profile.waveform.targetIntegral', {n: targetTotalCount.toLocaleString()})}</span>
                                    <span>{t('profile.waveform.scaleRatio', {n: scalePreview})}</span>
                                    <span>{t('profile.waveform.normalizedPeak', {n: Math.round(peakQps * (targetTotalCount / (integral || 1)))})}</span>
                                </div>
                            </>
                        )}
                    </div>

                    {/* ── Normalization panel ── */}
                    {targetTotalCount && !targetUnreachable && (
                        <div style={{marginTop: 10}}>
                            {normalizeError && (
                                <Alert
                                    type="error"
                                    message={normalizeError}
                                    showIcon
                                    closable
                                    onClose={() => setNormalizeError(null)}
                                    style={{marginBottom: 8, fontSize: 12}}
                                />
                            )}
                            <Button
                                type="primary"
                                size="small"
                                disabled={integral === 0 || targetUnreachable}
                                onClick={handleNormalize}
                            >
                                {t('profile.waveform.applyNormalize', {n: targetTotalCount.toLocaleString()})}
                            </Button>
                        </div>
                    )}
                </Col>

                {/* ─── Right: Table + slope summary ──────────── */}
                <Col flex="42%">
                    <Table<ControlPoint>
                        dataSource={pts}
                        columns={columns}
                        rowKey="key"
                        size="small"
                        pagination={false}
                        scroll={{y: 340}}
                        style={{marginBottom: 8}}
                        footer={() => (
                            <Button
                                type="dashed"
                                size="small"
                                icon={<PlusOutlined/>}
                                onClick={handleAddRow}
                            >
                                {t('profile.waveform.addRow')}
                            </Button>
                        )}
                    />

                    {/* Segment slope summary */}
                    <div style={{fontSize: 12, color: '#555'}}>
                        <div style={{fontWeight: 600, marginBottom: 4, color: '#333'}}>{t('profile.waveform.slopeSummary')}</div>
                        {slopeRows.map((row, i) => (
                            <div key={i} style={{display: 'flex', gap: 8, marginBottom: 2}}>
                                <span style={{color: '#999', minWidth: 110}}>
                                    {row.from} → {row.to}
                                </span>
                                <Tag
                                    color={row.dir === 'up' ? 'blue' : row.dir === 'down' ? 'orange' : 'default'}
                                    style={{fontSize: 11}}
                                >
                                    {row.dir === 'up' ? t('profile.waveform.slopeUp') : row.dir === 'down' ? t('profile.waveform.slopeDown') : t('profile.waveform.slopeFlat')}
                                </Tag>
                                <span style={{color: '#777'}}>
                                    {t('profile.waveform.slopeValue', {slope: (row.slope > 0 ? '+' : '') + row.slope.toFixed(2)})}
                                </span>
                            </div>
                        ))}
                    </div>
                </Col>
            </Row>
        </div>
    );
}
