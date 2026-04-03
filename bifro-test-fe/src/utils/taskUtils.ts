import {TaskStatusValues, TaskTypeValues} from '../types/task';

/**
 * 状态映射配置
 */
export const statusConfig: Record<string, {text: string; color: string}> = {
    [TaskStatusValues.INIT]: {text: '已创建', color: 'default'},
    [TaskStatusValues.START]: {text: '启动中', color: 'processing'},
    [TaskStatusValues.CONNECTING]: {text: '连接中', color: 'processing'},
    [TaskStatusValues.INIT_PUB_CLIENT]: {text: '初始化发布端', color: 'processing'},
    [TaskStatusValues.INIT_SUB_CLIENT]: {text: '初始化订阅端', color: 'processing'},
    [TaskStatusValues.ONGOING]: {text: '运行中', color: 'processing'},
    [TaskStatusValues.SHUTDOWN]: {text: '已完成', color: 'success'},
    [TaskStatusValues.STOPPED]: {text: '已停止', color: 'warning'},
};

/**
 * 任务类型映射配置
 */
export const taskTypeConfig: Record<string, {text: string; color: string}> = {
    [TaskTypeValues.CONN]: {text: '连接', color: 'blue'},
    [TaskTypeValues.PUBSUB]: {text: '发布/订阅', color: 'green'}
};

/**
 * 获取状态文本
 */
export const getStatusText = (status: string): string => {
    return statusConfig[status]?.text || status;
};

/**
 * 获取状态颜色
 */
export const getStatusColor = (status: string): string => {
    return statusConfig[status]?.color || 'default';
};

/**
 * 获取任务类型文本
 */
export const getTaskTypeText = (type: string): string => {
    return taskTypeConfig[type]?.text || type;
};

/**
 * 获取任务类型颜色
 */
export const getTaskTypeColor = (type: string): string => {
    return taskTypeConfig[type]?.color || 'default';
};

/**
 * 判断任务是否可以编辑
 */
export const canEditTask = (status: string): boolean => {
    return status === TaskStatusValues.INIT;
};

/**
 * 判断任务是否可以分配
 */
export const canAssignTask = (status: string): boolean => {
    return status === TaskStatusValues.INIT;
};

/**
 * 判断任务是否可以确认
 */
export const canConfirmTask = (status: string): boolean => {
    return status === TaskStatusValues.INIT;
};

/**
 * 判断任务是否可以停止
 */
export const canStopTask = (status: string): boolean => {
    return status === TaskStatusValues.ONGOING;
};

/**
 * 判断任务是否可以删除
 */
export const canDeleteTask = (status: string): boolean => {
    return [
        TaskStatusValues.INIT,
        TaskStatusValues.STOPPED,
        TaskStatusValues.SHUTDOWN
    ].includes(status as 'INIT' | 'STOPPED' | 'SHUTDOWN');
};

/**
 * 可选择的任务状态列表
 */
export const selectableStatuses = [
    TaskStatusValues.INIT,
    TaskStatusValues.STOPPED,
    TaskStatusValues.SHUTDOWN
];

/**
 * 判断任务是否可以被选择（用于批量操作）
 */
export const canSelectTask = (status: string): boolean => {
    return selectableStatuses.includes(status as 'INIT' | 'STOPPED' | 'SHUTDOWN');
};

/**
 * 协议选项
 */
export const protocolOptions = [
    {label: 'MQTT 3.1', value: 'MQTT_3_1'},
    {label: 'MQTT 3.1.1', value: 'MQTT_3_1_1'},
    {label: 'MQTT 5.0', value: 'MQTT_5_0'},
];

/**
 * 任务类型选项
 */
export const taskTypeOptions = [
    {label: '连接测试', value: TaskTypeValues.CONN},
    {label: '发布/订阅测试', value: TaskTypeValues.PUBSUB},
];
