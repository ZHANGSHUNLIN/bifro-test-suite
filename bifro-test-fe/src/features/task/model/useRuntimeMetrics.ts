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

import {useEffect, useRef, useState} from 'react';
import type {NodeMetricsResponse} from '../domain';

export interface AggregatedRuntimeMetrics {
    connectSuccessRate: number | null;
    messageReceivedCount: number;
    p95Latency: number | null;
    onlineNodes: number;
    totalNodes: number;
}

interface UseRuntimeMetricsOptions {
    taskId: string | undefined;
    nodeIds: string[];
}

function aggregate(responses: NodeMetricsResponse[], totalNodes: number): AggregatedRuntimeMetrics {
    let totalConnectSuccess = 0;
    let totalConnectException = 0;
    let totalMessageReceived = 0;
    let maxP95 = 0;
    let hasTimerData = false;
    let onlineNodes = 0;

    for (const data of responses) {
        if (!data.success) continue;
        onlineNodes++;
        for (const counter of data.counterMetrics ?? []) {
            if (counter.name === 'bifro_task_metric_connect_success_count') {
                totalConnectSuccess += counter.count;
            } else if (counter.name === 'bifro_task_metric_connect_exception_count') {
                totalConnectException += counter.count;
            } else if (counter.name === 'bifro_task_metric_message_received_count') {
                totalMessageReceived += counter.count;
            }
        }
        for (const timer of data.timerMetrics ?? []) {
            if (timer.hasData && timer.name === 'bifro_task_metric_connect_latency') {
                if (timer.p95 > maxP95) maxP95 = timer.p95;
                hasTimerData = true;
            }
        }
    }

    const totalAttempts = totalConnectSuccess + totalConnectException;
    return {
        connectSuccessRate: totalAttempts > 0
            ? Math.round(totalConnectSuccess / totalAttempts * 10000) / 100
            : null,
        messageReceivedCount: totalMessageReceived,
        p95Latency: hasTimerData ? Math.round(maxP95 * 100) / 100 : null,
        onlineNodes,
        totalNodes,
    };
}

export const useRuntimeMetrics = ({taskId, nodeIds}: UseRuntimeMetricsOptions) => {
    const [runtimeLoading, setRuntimeLoading] = useState(false);
    const [runtimeMetrics, setRuntimeMetrics] = useState<AggregatedRuntimeMetrics | null>(null);
    const esRef = useRef<EventSource | null>(null);
    const nodeIdsKey = nodeIds.join(',');

    useEffect(() => {
        if (esRef.current) {
            esRef.current.close();
            esRef.current = null;
        }

        if (!taskId || nodeIds.length === 0) return;

        setRuntimeLoading(true);
        const url = `/api/node/metrics/stream?taskId=${encodeURIComponent(taskId)}&nodeIds=${encodeURIComponent(nodeIdsKey)}`;
        const es = new EventSource(url);
        esRef.current = es;

        es.onmessage = (event) => {
            try {
                const responses: NodeMetricsResponse[] = JSON.parse(event.data);
                setRuntimeMetrics(aggregate(responses, nodeIds.length));
            } catch (e) {
                console.error('Failed to parse SSE metrics event', e);
            }
            setRuntimeLoading(false);
        };

        es.onerror = () => {
            setRuntimeLoading(false);
            es.close();
            esRef.current = null;
        };

        return () => {
            es.close();
            esRef.current = null;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [taskId, nodeIdsKey]);

    // No-op: SSE auto-streams; kept for interface compatibility
    const handleLoadRuntimeMetrics = () => {
    };

    return {runtimeLoading, runtimeMetrics, handleLoadRuntimeMetrics};
};
