export interface MqttBrokerConfig {
  id: string;
  brokerId?: string;
  name: string;
  host: string;
  port: number;
  description?: string;
  enabled: boolean;
  sslEnabled?: boolean;
  username?: string;
  password?: string;
  keepAliveSeconds?: number;
  connectionTimeoutSeconds?: number;
  maxConnections: number;
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
  sslEnabled?: boolean;
  lastHealthCheck?: string;
  createdAt?: string;
  updatedAt?: string;
}