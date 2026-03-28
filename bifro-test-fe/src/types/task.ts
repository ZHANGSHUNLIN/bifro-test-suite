// 任务管理相关的 TypeScript 类型定义

// 任务类型枚举
export type TaskType = 'CONN' | 'PUBSUB';
export const TaskTypeValues = {CONN: 'CONN', PUBSUB: 'PUBSUB'} as const;


// 协议类型
export type Protocol = 'tcp' | 'ssl' | 'ws' | 'wss';

// MQTT QoS 等级

export type MqttQoS = 0 | 1 | 2;

export const MqttQoSValues = {
    AT_MOST_ONCE: 0,
    AT_LEAST_ONCE: 1,
    EXACTLY_ONCE: 2,
} as const;

// 自定义参数请求对象
export interface CustomSpecificParamRequest {
    username?: string;
    password?: string;
    tenantId?: string;
    type?: string; // ClientTaskType
    topic?: string;
    count?: number;
    clientId?: string;
}

// WillConfig 遗嘱配置
export interface WillConfig {
    willFlag?: boolean;      // 是否启用遗嘱消息
    willTopic?: string;       // 遗嘱主题
    willMessage?: string;     // 遗嘱消息内容
    willMessageLen?: number;  // 遗嘱消息长度
    willQos?: number;         // 遗嘱QoS等级 (0-2)
    willRetain?: boolean;     // 遗嘱是否保留
}

// Broker信息
export interface BrokerItem {
    host: string;
    port: number;
    brokerId?: string;
    name?: string;
    description?: string;
    enabled?: boolean;
    group?: string; // 分组/项目名称
}

// 任务请求对象
export interface TaskRequest {
    taskName?: string;
    taskType: TaskType;
    protocol?: string;
    group?: string; // 分组/项目名称
    autoMultiAddress?: boolean;
    localAddresses?: string[];
    brokers: BrokerItem[];
    port?: number;
    customSpecificParamList?: CustomSpecificParamRequest[];
    username?: string;
    password?: string;
    tenantId?: string;
    thingIdStartAt?: number;
    thingIdPrefix?: string;
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
    topic?: string;
    qos?: MqttQoS;
    fixedTopic?: boolean;
    wildcard?: boolean;
    messageSize?: number;
    pubIntervalInMs?: number;
    stressDurationInSec?: number;
    stageTimeoutInSec?: number;
    delayAfterReadyInSec?: number;
    skipStatsPeriod?: number;
    retain?: boolean;
    mqtt5?: boolean;
    authType?: string;
    isEmptyClientId?: boolean;
    expiryIntervalInSec?: number;
    pubOnly?: boolean;
    subOnly?: boolean;
    tagPeriodIntervalInSec?: number;
    connectRate?: number;
    disconnectRate?: number;
    lifecycleActions?: string[];
    lifecycleActionsConfig?: Record<string, any>;
    willConfig?: WillConfig;
    exceptionEnds?: boolean;
}

// 任务配置对象
export interface TaskConfig {
    taskId?: string;
    taskType: TaskType;
    protocol: string;
    group?: string; // 分组/项目名称
    brokers: BrokerItem[];
    port: number;
    customSpecificParamList?: CustomSpecificParamRequest[];
    username?: string;
    password?: string;
    tenantId?: string;
    thingIdStartAt?: number;
    thingIdPrefix?: string;
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
    topic?: string;
    qos?: MqttQoS;
    fixedTopic?: boolean;
    wildcard?: boolean;
    messageSize?: number;
    pubIntervalInMs?: number;
    stressDurationInSec?: number;
    stageTimeoutInSec?: number;
    delayAfterReadyInSec?: number;
    skipStatsPeriod?: number;
    retain?: boolean;
    mqtt5?: boolean;
    authType?: string;
    emptyClientId?: boolean;
    expiryIntervalInSec?: number;
    pubOnly?: boolean;
    subOnly?: boolean;
    connectRate?: number;
    disconnectRate?: number;
    tagPeriodIntervalInSec?: number;
    enableAutoMultiAddress?: boolean;
    willConfig?: WillConfig;
    lifecycleActions?: string[];
    lifecycleActionsConfig?: Record<string, any>;
    exceptionEnds?: boolean;
    taskWorkStage?: string;
    createdAt?: string;
    updatedAt?: string;
}

// 任务统计信息
export interface TaskStatistics {
    totalNodes?: number;
    totalAssignedClients?: number;
    minClientsPerNode?: number;
    maxClientsPerNode?: number;
    averageClientsPerNode?: number;
    distributionBalance?: number;
}

// 任务详情响应
export interface TaskDetailResponse {
    success: boolean;
    message?: string;
    taskId?: string;
    taskName?: string;
    group?: string; // 分组/项目名称
    mainTask?: TaskConfig;
    brokers: BrokerItem[];
    subTasks?: Record<string, TaskConfig>;
    statistics?: TaskStatistics;
    timestamp?: number;
}

// 集群节点信息
export interface ClusterNodeInfo {
    nodeId: string;
    host: string;
    port: number;
    status: string;
    cpu?: number;
    memory?: number;
    lastHeartbeat?: string;
}

// 简化的任务列表项（用于表格显示）
export interface TaskListItem {
    id: string;
    taskId: string;
    taskName?: string;
    taskType: TaskType;
    protocol: string;
    group?: string; // 分组/项目名称
    brokers: BrokerItem[];
    totalClientCount: number;
    status: string;
    nodeId?: string;
    createdAt?: string;
    updatedAt?: string;
}

// 任务状态枚举
export type TaskStatus = 'INIT' | 'ASSIGNED' | 'ONGOING' | 'COLLECTING' | 'SHUTDOWN_ING' | 'SHUTDOWN' | 'STOPPED';

// 运行时常量（用于代码中引用，例如 TaskStatusValues.ONGOING）
export const TaskStatusValues = {
    INIT: 'INIT',
    ONGOING: 'ONGOING',
    ASSIGNED: 'ASSIGNED',
    COLLECTING: 'COLLECTING',
    SHUTDOWN_ING: 'SHUTDOWN_ING',
    SHUTDOWN: 'SHUTDOWN',
    STOPPED: 'STOPPED',
} as const;

// 任务状态文本映射
export const TaskStatusText = {
    [TaskStatusValues.INIT]: '初始化',
    [TaskStatusValues.ASSIGNED]: '已分配',
    [TaskStatusValues.ONGOING]: '进行中',
    [TaskStatusValues.COLLECTING]: '收集中',
    [TaskStatusValues.SHUTDOWN_ING]: '关闭中',
    [TaskStatusValues.SHUTDOWN]: '已关闭',
    [TaskStatusValues.STOPPED]: '已停止'
}

// 基础统计结果
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

// 订阅统计结果
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

// 发布统计结果
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

// 连接统计结果 (嵌套在 actualResult 中)
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

// 连接统计结果
export interface StatsConnResult {
    expectConnCount?: number;
    actualConnCount?: number;
    expectConnQps?: number;
    actualConnQps?: number;
    connectFailCount?: number;
    actualResult?: StatsConnActualResult;
}

// 任务报告（节点+任务的详情）
export interface TaskReport {
    id?: string;
    taskId: string;
    nodeId: string;
    taskType?: 'CONN' | 'PUBSUB';
    taskStage?: string;
    taskWorkStage?: string;
    statsBasicResult?: StatsBasicResult;
    statsSubResult?: StatsSubResult;
    statsPubResult?: StatsPubResult;
    statsConnResult?: StatsConnResult;
    createTime?: string;
}

// 分页信息 - 与后端 PageInfo 对应
export interface PageInfo<T> {
    content: T[];           // 当前页数据
    totalElements: number;  // 总记录数
    totalPages: number;     // 总页数
    size: number;           // 每页大小
    number: number;         // 当前页码（从0开始）
    numberOfElements: number; // 当前页实际元素数量
    first: boolean;         // 是否第一页
    last: boolean;          // 是否最后一页
}

// 节点任务分配信息
export interface NodeAllocation {
    nodeId: string;
    allocatedClientCount: number;
}

// 任务分配计算结果
export interface NodeTaskAllocationVO {
    totalClientCount: number;
    nodeAllocationList: NodeAllocation[];
}