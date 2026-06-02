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

// TypeScript type definitions for task management DTO/model layer
import type {MqttQoS, TaskTemplate, TaskType} from './task.constants';
import type {ProfileRef, WaveformProfile} from '../../profile';

// Custom parameter request object
export interface CustomSpecificParamRequest {
    username?: string;
    password?: string;
    type?: string; // ClientTaskType
    topic?: string;
    count?: number;
    clientId?: string;
}

// WillConfig will message configuration
export interface WillConfig {
    willFlag?: boolean;      // whether to enable will message
    willTopic?: string;       // will topic
    willMessage?: string;     // will message content
    willMessageLen?: number;  // will message length
    willQos?: number;         // will QoS level (0-2)
    willRetain?: boolean;     // whether will is retained
}

// Broker info
export interface BrokerItem {
    host: string;
    port: number;
    brokerId?: string;
    name?: string;
    description?: string;
    enabled?: boolean;
    group?: string; // groupID
}

// Task request object
export interface TaskRequest {
    taskName?: string;
    taskType: TaskType;
    protocol: string;
    template: TaskTemplate; // task template type
    group: string; // groupID
    autoMultiAddress?: boolean;
    localAddresses?: string[];
    brokers: BrokerItem[];
    port?: number;
    customSpecificParamList?: CustomSpecificParamRequest[];
    username?: string;
    password?: string;
    thingIdStartAt?: number;
    clientCertEnabled?: boolean; // whether client certificate is enabled
    clientCertId?: string; // client certificate ID
    cleanSession?: boolean;
    keepAliveInSec?: number;
    ackTimeoutInSec?: number;
    reconnectMaxAttempts?: number;
    reconnectIntervalInMs?: number;
    connectTimeoutInMs?: number;
    maxInflightQueue?: number;
    totalClientCount?: number;
    fanOut?: number;
    fanIn?: number;
    topicsPerClient?: number;
    topic?: string;
    qos?: MqttQoS;
    fixedTopic?: boolean;
    messageSize?: number;
    publishRate?: number;
    stressDurationInSec?: number;
    stageTimeoutInSec?: number;
    delayAfterStageInSec?: number;
    retain?: boolean;
    mqtt5?: boolean;
    isMqtt5?: boolean;
    emptyClientId?: boolean;
    authType?: string;
    isEmptyClientId?: boolean;
    expiryIntervalInSec?: number;
    connectRate?: number;
    disconnectRate?: number;
    connectWaveQpsSpec?: WaveQpsSpec;
    disconnectWaveQpsSpec?: WaveQpsSpec;
    willConfig?: WillConfig;
    payloadMode?: 'BIFRO' | 'TEMPLATE' | 'RANDOM';
    payloadTemplate?: string;
    waveQpsSpec?: WaveQpsSpec;
    qpsMode?: 'FIXED' | 'WAVE' | 'DYNAMIC';
    profileConfig?: ProfileRef;
    publishQpsMode?: 'FIXED' | 'DYNAMIC';
    publishProfileId?: string;
    chaosPolicy?: ChaosPolicy;
    // ── Connect / Disconnect profile ──
    connectProfileId?: string;
    disconnectProfileId?: string;
    // ── Subscribe QPS ──
    subscribeQpsMode?: 'FIXED' | 'DYNAMIC';
    subscribeRate?: number;
    subscribeProfileId?: string;
}

export interface WaveQpsComponent {
    amplitude: number;
    periodFraction: number;
    phase: number;
}

export interface WaveQpsSpec {
    baseQps: number;
    totalDurationMs: number;
    components: WaveQpsComponent[];
}

export type ChaosBehaviorName =
    | 'DUPLICATE_PUBACK'
    | 'EXCEED_INFLIGHT_WINDOW'
    | 'DOUBLE_CONNECT'
    | 'INVALID_PACKET_ID_ZERO'
    | 'OVERSIZED_PAYLOAD'
    | 'MALFORMED_TOPIC';

export interface ChaosPolicy {
    behaviors: ChaosBehaviorName[];
    targetRatio: number;
    maxInflight?: number;
    maxPacketSizeOverride?: number;
}

// Task config object
export interface TaskConfig {
    taskId?: string;
    taskType: TaskType;
    template?: TaskTemplate; // task template type
    protocol: string;
    group?: string; // groupID
    brokers: BrokerItem[];
    port: number;
    customSpecificParamList?: CustomSpecificParamRequest[];
    username?: string;
    password?: string;
    thingIdStartAt?: number;
    cleanSession?: boolean;
    localAddresses?: string[];
    keepAliveInSec?: number;
    ackTimeoutInSec?: number;
    reconnectMaxAttempts?: number;
    reconnectIntervalInMs?: number;
    connectTimeoutInMs?: number;
    maxInflightQueue?: number;
    totalClientCount?: number;
    fanOut?: number;
    fanIn?: number;
    topicsPerClient?: number;
    topic?: string;
    qos?: MqttQoS;
    fixedTopic?: boolean;
    messageSize?: number;
    publishRate?: number;
    stressDurationInSec?: number;
    stageTimeoutInSec?: number;
    delayAfterStageInSec?: number;
    retain?: boolean;
    mqtt5?: boolean;
    isMqtt5?: boolean;
    authType?: string;
    isEmptyClientId?: boolean;
    expiryIntervalInSec?: number;
    connectRate?: number;
    disconnectRate?: number;
    connectWaveQpsSpec?: WaveQpsSpec;
    disconnectWaveQpsSpec?: WaveQpsSpec;
    enableAutoMultiAddress?: boolean;
    willConfig?: WillConfig;
    clientCertId?: string; // client certificate ID
    taskWorkStage?: string;
    createdAt?: string;
    updatedAt?: string;
    // Message payload config (passed through from TaskRequest, readable via details API)
    payloadMode?: 'BIFRO' | 'TEMPLATE' | 'RANDOM';
    payloadTemplate?: string;
    waveQpsSpec?: WaveQpsSpec;
    qpsMode?: 'FIXED' | 'WAVE' | 'DYNAMIC';
    profileConfig?: ProfileRef;
    publishQpsMode?: 'FIXED' | 'DYNAMIC';
    publishProfileId?: string;
    chaosPolicy?: ChaosPolicy;
    // ── Connect / Disconnect profile ──
    connectProfileId?: string;
    disconnectProfileId?: string;
    // ── Subscribe QPS ──
    subscribeQpsMode?: 'FIXED' | 'DYNAMIC';
    subscribeRate?: number;
    subscribeProfileId?: string;
}

// Task statistics
export interface TaskStatistics {
    // Assignment overview
    totalNodes?: number;
    totalAssignedClients?: number;
    minClientsPerNode?: number;
    maxClientsPerNode?: number;
    averageClientsPerNode?: number;
    distributionBalance?: number;
    // Running metrics snapshot (completed/stopped tasks)
    actualDurationMs?: number;          // actual run duration (ms)
    // Connection metrics
    totalConnectSuccess?: number;
    totalConnectException?: number;
    totalReconnect?: number;
    totalClientCreated?: number;
    totalClientFailure?: number;
    // Message metrics
    totalMessageReceived?: number;
    totalMessageDuplicate?: number;
    totalPublishCompletion?: number;
    // Connection latency (ms)
    connectLatencyP50?: number;
    avgConnectLatencyP95?: number;      // P95 connection latency
    connectLatencyP99?: number;
    connectLatencyMax?: number;
    // End-to-end latency (ms, PUBSUB)
    endToEndLatencyP50?: number;
    endToEndLatencyP95?: number;
    endToEndLatencyP99?: number;
    // Puback latency
    pubackLatencyP95?: number;
}

// Task detail response
export interface TaskDetailResponse {
    success: boolean;
    message?: string;
    taskId?: string;
    taskName?: string;
    group?: string; // groupID
    mainTaskView?: TaskConfigView;
    publishProfile?: WaveformProfile;
    brokers: BrokerItem[];
    subTasks?: Record<string, TaskConfigView>;
    subTaskDetails?: Record<string, SubTaskDetail>;
    statistics?: TaskStatistics;
    timestamp?: number;
    createTime?: number;      // task create time (ms timestamp)
    startTime?: number;       // task actual start time (ms timestamp)
    endTime?: number;         // task end time (ms timestamp)
    metricsFromSnapshot?: boolean;  // whether metrics are from snapshot
    // Pipeline stage list (provided by backend)
    pipelineStages?: PipelineStageInfo[];
    currentStageIndex?: number;
    // State machine transitions (dynamically generated by backend based on task template)
    stateTransitions?: StateTransition[];
}

// State transition metadata
export interface StateTransition {
    from?: string;   // source state
    to?: string;     // target state
    event?: string;  // trigger event
}

// State change history entry (with timestamp)
export interface StateHistoryItem {
    fromStage?: string;
    toStage?: string;
    triggerEvent?: string;
    timestamp?: number;   // ms timestamp
    nodeId?: string;
    nodeName?: string;
    errorMessage?: string;
    source?: string;      // MAIN_TASK / SUB_TASK
}

export interface TaskDiagnosticSymptom {
    type: string;
    severity: 'INFO' | 'WARN' | 'CRIT' | string;
    nodeId?: string;
    stage?: string;
    message?: string;
    details?: Record<string, unknown>;
}

export interface TaskPipelineDiagnostic {
    nodeId?: string;
    nodeName?: string;
    taskType?: string;
    taskWorkStage?: string;
    totalClientCount?: number;
    stages?: PipelineStageSnapshot[];
}

export interface TaskDiagnosticsResponse {
    taskId: string;
    generatedAt: number;
    window?: {
        startMs?: number;
        endMs?: number;
    };
    taskSnapshot?: TaskDetailResponse;
    subtasks?: SubTaskDetail[];
    stateHistory?: StateHistoryItem[];
    pipelineDiagnostics?: TaskPipelineDiagnostic[];
    symptoms?: TaskDiagnosticSymptom[];
    nextActions?: string[];
    logFiles?: string[];
    logQueryKeys?: string[];
}

export interface TaskLogSummaryEntry {
    file?: string;
    line?: string;
}

export interface TaskLogSummaryResponse {
    taskId: string;
    generatedAt: number;
    files?: string[];
    lines?: TaskLogSummaryEntry[];
}

// Pipeline stage info
export interface PipelineStageInfo {
    key: string;    // stage identifier
    label: string;  // stage display name
}

// Task basic info response
export interface TaskBasicInfoResponse {
    taskId: string;
    taskName?: string;
    group?: string;
    mainTaskView?: TaskConfigView;
    publishProfile?: WaveformProfile;
    brokers?: BrokerItem[];
    createTime?: number;
    startTime?: number;
    endTime?: number;
}

// Task statistics response
export interface TaskStatisticsResponse {
    taskId: string;
    metricsFromSnapshot?: boolean;
    statistics?: TaskStatistics;
}

// Task subtask response
export interface TaskSubTasksResponse {
    taskId: string;
    subTasks?: Record<string, TaskConfigView>;
    subTaskDetails?: Record<string, SubTaskDetail>;
}

export interface TaskConfigView {
    taskId?: string;
    nodeId?: string;
    taskType?: 'CONN' | 'PUBSUB' | 'CHAOS';
    template?: string;
    totalClientCount?: number;
    stressDurationInSec?: number;
    taskWorkStage?: string;
    plannedStartAtMs?: number;
}

// Subtask details (backend-enriched data)
export interface SubTaskDetail {
    nodeId: string;
    nodeName: string;
    taskType: string;
    totalClientCount: number;
    taskWorkStage: string;
    quickMetrics?: string; // key metrics quick-view string (frontend-computed)
    // Metrics snapshot (completed/stopped tasks)
    counterMetrics?: CounterMetricData[];
    timerMetrics?: TimerMetricData[];
    // Pipeline stage execution snapshots (real-time updates)
    pipelineStages?: PipelineStageSnapshot[];
}

// Pipeline single-stage execution snapshot (real-time push from backend worker)
export interface PipelineStageSnapshot {
    key: string;           // stage getName(), unique identifier
    label: string;         // display name
    visible?: boolean;     // whether stage should be shown in UI
    status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED' | 'SKIPPED' | 'CANCELLED';
    startedAt?: number;    // epoch ms, stage start time
    endedAt?: number;      // epoch ms, stage end time
    failureReason?: string; // failure reason (non-null when FAILED)
    durationMs?: number;
    started?: number;
    completed?: number;
    cancelled?: number;
    failed?: number;
    pending?: number;
    pendingSamples?: string[];
    failureReasons?: Record<string, number>;
}

// Node metrics - counter metric data
export interface CounterMetricData {
    name: string;
    tags: Record<string, string>;
    count: number;
}

// Node metrics - timer metric data
export interface TimerMetricData {
    name: string;
    tags: Record<string, string>;
    count: number;
    mean: number;
    p50: number;
    p95: number;
    p99: number;
    max: number;
    totalTime: number;
    hasData: boolean;
}

// Node metrics response
export interface NodeMetricsResponse {
    nodeId: string;
    success: boolean;
    errorCode?: string;
    errorMessage?: string;
    timestamp: number;
    counterMetrics: CounterMetricData[];
    timerMetrics: TimerMetricData[];
}

// Cluster node info
export interface ClusterNodeInfo {
    nodeId: string;
    host: string;
    port: number;
    status: string;
    cpu?: number;
    memory?: number;
    lastHeartbeat?: string;
}

// Simplified task list item (for table display)
export interface TaskListItem {
    id: string;
    taskId: string;
    taskName?: string;
    taskType: TaskType;
    protocol: string;
    group?: string; // groupID
    brokers: BrokerItem[];
    totalClientCount: number;
    status: string;
    nodeId?: string;
    createTime?: string;  // aligned with backend TaskListVO.createTime (legacy, may be null)
    createTimeMs?: number; // backend TaskListVO.getCreateTimeMs(), ms timestamp
    updatedAt?: string;
    taskConfig?: TaskConfig; // used to pass full config when copying task
}

// Base statistics result
export interface StatsBasicResult {
    count?: number;
    qps?: number;
    meanLatency?: number;
    standardDeviation?: number;
    medianLatency?: number;
    p95Latency?: number;
    p99Latency?: number;
    p999Latency?: number;
    maxLatency?: number;
    minLatency?: number;
    bucketCounts?: number[][];
    timestamp?: number;
}

// Subscribe statistics result
export interface StatsSubResult {
    count?: number;
    qps?: number;
    meanLatency?: number;
    standardDeviation?: number;
    medianLatency?: number;
    p95Latency?: number;
    p99Latency?: number;
    p999Latency?: number;
    maxLatency?: number;
    minLatency?: number;
    bucketCounts?: number[][];
    timestamp?: number;
}

// Publish statistics result
export interface StatsPubResult {
    count?: number;
    qps?: number;
    meanLatency?: number;
    standardDeviation?: number;
    medianLatency?: number;
    p95Latency?: number;
    p99Latency?: number;
    p999Latency?: number;
    maxLatency?: number;
    minLatency?: number;
    bucketCounts?: number[][];
    timestamp?: number;
}

// Connection statistics result (nested in actualResult)
export interface StatsConnActualResult {
    count: number;
    qps: number;
    meanLatency: number;
    standardDeviation: number;
    medianLatency: number;
    p95Latency: number;
    p99Latency: number;
    p999Latency: number;
    maxLatency: number;
    minLatency: number;
    bucketCounts?: number[][];
    timestamp: number;
}

// Connection statistics result
export interface StatsConnResult {
    expectConnCount?: number;
    actualConnCount?: number;
    expectConnQps?: number;
    actualConnQps?: number;
    connectFailCount?: number;
    actualResult?: StatsConnActualResult;
}

// Pagination info - corresponds to backend PageInfo
export interface PageInfo<T> {
    content: T[];           // current page data
    totalElements: number;  // total record count
    totalPages: number;     // total page count
    size: number;           // Page size
    number: number;         // current page number (0-indexed)
    numberOfElements: number; // actual element count on current page
    first: boolean;         // whether first page
    last: boolean;          // whether last page
}

// Node task allocation info
export interface NodeAllocation {
    nodeId: string;
    allocatedClientCount: number;
}

// Task allocation request
export interface NodeTaskAllocationRequest {
    totalClientCount: number;
    nodeAllocationList: NodeAllocation[];
}

// Task report response
export interface TaskReportResponse {
    taskId: string;
    taskName?: string;
    taskType?: string;
    startTime?: number;
    endTime?: number;
    durationMs?: number;
    // Throughput metrics
    totalMessagesSent?: number;
    totalMessagesReceived?: number;
    totalBytesTransmitted?: number;
    avgMessagesPerSecond?: number;
    avgBytesPerSecond?: number;
    avgConnectQps?: number;
    avgPublishQps?: number;
    avgReceiveQps?: number;
    // Latency metrics
    latencyP50?: number;
    latencyP95?: number;
    latencyP99?: number;
    latencyMax?: number;
    connectLatencyP95?: number;
    pubackLatencyP95?: number;
    // Connection metrics
    totalConnectSuccess?: number;
    totalConnectFailure?: number;
    connectSuccessRate?: number;
    totalReconnectCount?: number;
    // Message quality metrics
    totalDuplicateMessages?: number;
    duplicateRate?: number;
    estimatedMessageLoss?: number;
    messageLossRate?: number;
    // QoS distribution
    qosDistribution?: QosDistribution;
    // Node metrics
    totalNodes?: number;
    onlineNodes?: number;
    nodeReports?: NodeReport[];
    // Client metrics
    totalClients?: number;
    failedClients?: number;
    // Exception statistics
    errorCounts?: Record<string, number>;
    // Chaos test results: behavior → brokerReaction → count
    chaosResults?: Record<string, Record<string, number>>;
}

// QoS distribution statistics
export interface QosDistribution {
    qos0Count?: number;
    qos1Count?: number;
    qos2Count?: number;
    qos0Percent?: number;
    qos1Percent?: number;
    qos2Percent?: number;
}

// Single node report
export interface NodeReport {
    nodeId: string;
    nodeName?: string;
    assignedClients?: number;
    messagesSent?: number;
    messagesReceived?: number;
    latencyP95?: number;
    connectSuccess?: number;
    connectFailure?: number;
    avgConnectQps?: number;
    avgPublishQps?: number;
    avgReceiveQps?: number;
}

// Client instance info
export interface ClientInstance {
    clientId: string;
    host: string;
    port: number;
    localAddress?: string;
    localPort?: number;
    status: string;
    connectedAt?: number;
    clientType: string;
    pubCount?: number;
    subCount?: number;
}

// Client instance paginated response
export interface ClientInstanceResponse {
    success: boolean;
    errorMessage?: string;
    clients: ClientInstance[];
    total: number;
    page: number;
    size: number;
    totalPages: number;
}
