/**
 * MQTT Broker 分组相关类型定义
 */

export interface MqttGroup {
  id: string;
  name: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface GroupListItem {
  id: string;
  name: string;
  description?: string;
  brokerCount: number;
  createdAt?: string;
}

export interface GroupRequest {
  name: string;
  description?: string;
}
