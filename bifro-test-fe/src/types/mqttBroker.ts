export interface MqttBrokerConfig {
    id: string;
    brokerId?: string;
    name: string;
    host: string;
    port: number;
    description?: string;
    enabled: boolean;
    group?: string; // 分组/项目名称
    createdAt?: string;
    updatedAt?: string;
    lastHealthCheck?: string;
}

export interface BrokerListItem {
    id: string;
    brokerId: string;
    name: string;
    host: string;
    port: number;
    description?: string;
    enabled: boolean;
    group?: string; // 分组/项目名称
    lastHealthCheck?: string;
    createdAt?: string;
    updatedAt?: string;
}
