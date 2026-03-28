import {TaskTypeValues} from '../../types/task';

// 任务表单字段配置接口
export interface FormFieldConfig {
    name: string;
    label: string;
    type: 'input' | 'inputNumber' | 'select' | 'switch' | 'textarea' | 'datePicker' | 'password';
    required?: boolean;
    placeholder?: string;
    initialValue?: any;
    rules?: any[];
    options?: Array<{ label: string; value: string | number }>;
    width?: number | string;
    min?: number;
    max?: number;
    step?: number;
}

// 基础配置字段
export const basicFields: FormFieldConfig[] = [
    {
        name: 'taskName',
        label: '任务名称',
        type: 'input',
        placeholder: '请输入任务名称',
    },
    {
        name: 'taskType',
        label: '任务类型',
        type: 'select',
        required: true,
        initialValue: TaskTypeValues.CONN,
        options: [
            {label: '连接', value: TaskTypeValues.CONN},
            {label: '发布/订阅', value: TaskTypeValues.PUBSUB},
        ],
    },
    {
        name: 'protocol',
        label: '协议类型',
        type: 'select',
        required: true,
        initialValue: 'tcp',
        options: [
            {label: 'TCP', value: 'tcp'},
            // { label: 'SSL', value: 'ssl' },
            // { label: 'WebSocket', value: 'ws' },
            // { label: 'WebSocket Secure', value: 'wss' },
        ],
    },
    {
        name: 'totalClientCount',
        label: '客户端数量',
        type: 'inputNumber',
        required: true,
        initialValue: 100,
        min: 1,
        max: 10000000000,
    },
    {
        name: 'connectRate',
        label: '连接速率(个/秒)',
        type: 'inputNumber',
        initialValue: 100,
        min: 1,
        max: 10000000,
        step: 0.1,
    },
    {
        name: 'disconnectRate',
        label: '断开速率(个/秒)',
        type: 'inputNumber',
        initialValue: 2000,
        min: 0.1,
        max: 10000000,
        step: 0.1,
    },
    {
        name: 'fanOut',
        label: 'Fan Out',
        type: 'inputNumber',
        initialValue: 1,
        min: 1,
        max: 10000000000,
    },
    {
        name: 'fanIn',
        label: 'Fan In',
        type: 'inputNumber',
        initialValue: 1,
        min: 1,
        max: 10000000000,
    },
];

// 连接配置字段
export const connectionFields: FormFieldConfig[] = [
    {
        name: 'keepAliveInSec',
        label: '保活时间(秒)',
        type: 'inputNumber',
        initialValue: 120,
        min: 0,
        max: 3600,
    },
    {
        name: 'stageTimeoutInSec',
        label: '阶段超时(秒)',
        type: 'inputNumber',
        initialValue: 30,
        min: 1,
        max: 3600,
    },
    {
        name: 'delayAfterReadyInSec',
        label: '准备后延迟(秒)',
        type: 'inputNumber',
        initialValue: 1,
        min: 0,
        max: 300,
    },
    {
        name: 'skipStatsPeriod',
        label: '跳过统计周期',
        type: 'inputNumber',
        initialValue: 0,
        min: 0,
        max: 100,
    },
    {
        name: 'tagPeriodIntervalInSec',
        label: '标签周期间隔(秒)',
        type: 'inputNumber',
        initialValue: 30,
        min: 1,
        max: 3600,
    },
    {
        name: 'expiryIntervalInSec',
        label: '消息过期时间(秒)',
        type: 'inputNumber',
        initialValue: 120,
        min: 0,
        max: 86400,
    },
    {
        name: 'stressDurationInSec',
        label: '测试时长(秒)',
        type: 'inputNumber',
        initialValue: 60,
        min: 1,
        max: 86400,
    },
];



// 认证配置字段
export const authFields: FormFieldConfig[] = [
];

// 协议配置字段
export const protocolFields: FormFieldConfig[] = [
    {
        name: 'cleanSession',
        label: 'Clean Session',
        type: 'switch',
        initialValue: true,
    },
    {
        name: 'mqtt5',
        label: 'MQTT 5.0',
        type: 'switch',
        initialValue: false,
    },
    {
        name: 'autoMultiAddress',
        label: '自动多地址',
        type: 'switch',
        initialValue: true,
    },
    {
        name: 'wildcard',
        label: '通配符主题',
        type: 'switch',
        initialValue: false,
    },
];

// 客户端ID配置字段
export const clientIdFields: FormFieldConfig[] = [
    {
        name: 'emptyClientId',
        label: '空Client ID允许',
        type: 'switch',
        initialValue: false,
    },
];

// 连接超时配置字段
export const timeoutFields: FormFieldConfig[] = [
    {
        name: 'connectTimeoutInMs',
        label: '连接超时(毫秒)',
        type: 'inputNumber',
        initialValue: 10000,
        min: 100,
        max: 60000,
    },
    {
        name: 'ackTimeoutInSec',
        label: 'ACK超时(秒)',
        type: 'inputNumber',
        initialValue: 120,
        min: 1,
        max: 3600,
    },
    {
        name: 'reconnectMaxAttempts',
        label: '最大重连次数',
        type: 'inputNumber',
        initialValue: 10,
        min: 1,
        max: 100,
    },
    {
        name: 'reconnectIntervalInMs',
        label: '重连间隔(ms)',
        type: 'inputNumber',
        initialValue: 5000,
        min: 100,
        max: 30000,
    },
    {
        name: 'maxInflightQueue',
        label: '最大队列大小',
        type: 'inputNumber',
        initialValue: 200,
        min: 10,
        max: 10000,
    },

];

// Will配置字段
export const willFields: FormFieldConfig[] = [
    {
        name: 'willConfig.willFlag',
        label: '启用Will',
        type: 'switch',
        initialValue: false,
    },
    {
        name: 'willConfig.willTopic',
        label: 'Will Topic',
        type: 'input',
        placeholder: '例如: last/{clientId}',
        initialValue: 'last/{clientId}',
    },
    {
        name: 'willConfig.willMessage',
        label: 'Will Message',
        type: 'input',
        placeholder: 'Will消息内容',
        initialValue: 'last xxxxx',
    },
    {
        name: 'willConfig.willMessageLen',
        label: 'Will Message Length',
        type: 'inputNumber',
        placeholder: 'Will消息长度（字节）',
        initialValue: 10,
        min: 0,
        max: 65535,
    },
    {
        name: 'willConfig.willQos',
        label: 'Will QoS',
        type: 'select',
        initialValue: 1,
        options: [
            {label: 'QoS 0', value: 0},
            {label: 'QoS 1', value: 1},
            {label: 'QoS 2', value: 2},
        ],
    },
    {
        name: 'willConfig.willRetain',
        label: 'Will Retain',
        type: 'switch',
        initialValue: false,
    },
];

// 所有字段映射
export const formFieldGroups = {
    basic: basicFields,
    connection: connectionFields,
    auth: authFields,
    protocol: protocolFields,
    clientId: clientIdFields,
    timeout: timeoutFields,
    will: willFields,
};