/**
 * 任务分组相关类型定义
 */

export interface TaskGroup {
  id: string;
  name: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TaskGroupListItem {
  id: string;
  name: string;
  description?: string;
  taskCount: number;
  createdAt?: string;
}

export interface TaskGroupRequest {
  name: string;
  description?: string;
}
