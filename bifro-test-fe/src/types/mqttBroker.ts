export interface MqttBrokerConfig {
    id: string;
    brokerId?: string;
    name: string;
    host: string;
    port: number;
    description?: string;
    enabled: boolean;
    group?: string; // 分组ID
    createdAt?: string;
    updatedAt?: string;
}

export interface BrokerListItem {
    id: string;
    brokerId: string;
    name: string;
    host: string;
    port: number;
    description?: string;
    enabled: boolean;
    group?: string; // 分组ID
    createdAt?: string;
    updatedAt?: string;
}
