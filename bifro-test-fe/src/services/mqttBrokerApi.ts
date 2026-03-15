// MQTT Broker 管理相关的 API 服务
import { api } from '../utils/request';
import type { BrokerListItem, MqttBrokerConfig } from '../types/mqttBroker';
import type { PageInfo } from '../types/task';

// MQTT Broker 管理 API
export const mqttBrokerApi = {
  // 获取所有 Broker 列表（enabled为可选参数，为空时不传此参数）
  getAllBrokers: (enabled?: boolean) => {
    const params = enabled !== undefined ? { enabled } : undefined;
    return api.get<PageInfo<BrokerListItem>>('/broker/list', params ? { params } : undefined);
  },

  // 获取 Broker 详情
  getBrokerDetails: (brokerId: string) => {
    return api.get<MqttBrokerConfig>('/broker/:brokerId', {
      params: { brokerId }
    });
  },

  // 添加 Broker
  addBroker: (brokerRequest: any) => {
    return api.post<MqttBrokerConfig>('/broker/add', brokerRequest);
  },

  // 修改 Broker
  updateBroker: (brokerId: string, brokerRequest: any) => {
    return api.put<MqttBrokerConfig>('/broker/:brokerId', brokerRequest, {
      params: { brokerId }
    });
  },

  // 删除 Broker
  deleteBroker: (brokerId: string) => {
    return api.delete<void>('/broker/:brokerId', {
      params: { brokerId }
    });
  },

  // 启用/禁用 Broker
  toggleBrokerStatus: (brokerId: string, enabled: boolean) => {
    return api.patch<MqttBrokerConfig>('/broker/:brokerId/status', { enabled }, {
      params: { brokerId }
    });
  },

};

export default mqttBrokerApi;