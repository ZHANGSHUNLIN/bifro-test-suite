// 任务管理相关的 API 服务
import { api } from '../utils/request';
import type { TaskListItem, TaskDetailResponse, TaskReport, TaskConfig, PageInfo, NodeTaskAllocationVO } from '../types/task';

// 任务管理 API
export const taskApi = {
  // 获取所有任务列表
  getAllTasks: (taskName?: string, taskType?: string, pageNum: number = 1, pageSize: number = 20) => {
    const params: Record<string, string | number | boolean> = { pageNum, pageSize };
    if (taskName) {
      params.taskName = taskName;
    }
    if (taskType) {
      params.taskType = taskType;
    }
    return api.get<PageInfo<TaskListItem>>('/task/list', { params });
  },

  // 获取任务详情
  getTaskDetails: (id: string) => {
    return api.get<TaskDetailResponse>('/task/:id', {
      params: { id }
    });
  },

  // 获取任务报告（节点+任务详情）
  getTaskReport: (nodeId: string, taskId: string, pageNum: number = 1, pageSize: number = 20) => {
    return api.get<PageInfo<TaskReport>>('/task/taskReport', {
      params: { taskId, nodeId, pageNum, pageSize }
    });
  },

  // 添加任务
  addTask: (taskRequest: any) => {
    return api.post<TaskConfig>('/task', taskRequest);
  },

  // 修改任务
  updateTask: (id: string, taskRequest: any) => {
    return api.put<TaskConfig>('/task/:id', taskRequest, {
      params: { id }
    });
  },


  // 确认任务
  confirmTask: (id: string) => {
    return api.post<TaskDetailResponse>('/task/:id/confirmTask', undefined, {
      params: { id }
    });
  },

  // 分配任务 - 新的API接口
  assignTask: (taskId: string, allocationRequest?: NodeTaskAllocationVO) => {
    return api.post<TaskConfig>('/task/assign/:taskId', allocationRequest, {
      params: { taskId }
    });
  },

  // 计算任务分配（预分配）
  calculateNodeTaskAllocation: (taskId: string) => {
    return api.post<NodeTaskAllocationVO>('/task/calculate/:taskId', null, {
      params: { taskId }
    });
  },

  // 删除任务
  deleteTask: (id: string) => {
    return api.delete<TaskDetailResponse>('/task/:id', undefined, {
      params: { id }
    });
  },

  // 批量删除任务
  batchDeleteTask: (ids: string[]) => {
    return api.delete<string>('/task/batch', ids);
  },

  // 停止任务
  stopTask: (id: string) => {
    return api.post<string>('/task/stop/:id', undefined, {
      params: { id }
    });
  },

  // 获取所有节点信息
  getAllNodes: () => {
    return api.get<Record<string, any>>('/task/allNodes');
  },
};

export default taskApi;