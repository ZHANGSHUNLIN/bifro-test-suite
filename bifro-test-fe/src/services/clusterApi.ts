// 集群管理相关的 API 服务
import { api } from '../utils/request';

// 集群管理 API
export const clusterApi = {
  // 获取所有节点信息
  getAllNodes: () => {
    return api.get<Record<string, any>>('/task/allNodes');
  },

  // 获取集群统计信息
  getClusterStatistics: () => {
    return api.get<any>('/task/statistics');
  },

  // 重启节点
  restartNode: (nodeId: string) => {
    return api.post<void>('/task/nodes/:nodeId/restart', undefined, {
      params: { nodeId }
    });
  },

  // 停止节点
  stopNode: (nodeId: string) => {
    return api.post<void>('/task/nodes/:nodeId/stop', undefined, {
      params: { nodeId }
    });
  },

  // 启用节点
  enableNode: (nodeId: string) => {
    return api.post<void>('/task/nodes/:nodeId/enable', undefined, {
      params: { nodeId }
    });
  },

  // 禁用节点
  disableNode: (nodeId: string) => {
    return api.post<void>('/task/nodes/:nodeId/disable', undefined, {
      params: { nodeId }
    });
  },
};

export default clusterApi;