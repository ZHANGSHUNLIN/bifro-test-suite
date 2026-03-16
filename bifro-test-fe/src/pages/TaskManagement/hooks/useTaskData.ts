import {useCallback, useState} from 'react';
import {message} from 'antd';
import taskApi from '../../../services/taskApi';
import mqttBrokerApi from '../../../services/mqttBrokerApi';
import type {TaskListItem} from '../../../types/task';

export const useTaskData = () => {
  const [data, setData] = useState<TaskListItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  // 加载任务列表
  const loadTasks = useCallback(async (taskName?: string, taskType?: string) => {
    setIsLoading(true);
    setError(null);
    try {
      // 后端返回 ApiResponse<PageInfo<TaskListVO>>，request.ts 已提取 data 部分
      const pageInfo = await taskApi.getAllTasks(taskName, taskType);
      const taskListItems: TaskListItem[] = pageInfo.content.map((config: any) => ({
        id: config.id || '',
        taskId: config.taskId || '',
        taskName: config.taskName,
        taskType: config.taskType,
        protocol: config.protocol,
        brokers: config.brokers || config.hosts, // 兼容旧数据
        totalClientCount: config.totalClientCount || 0,
        status: config.taskWorkStage,
      }));
      setData(taskListItems);
    } catch (error) {
      setError(error as Error);
      message.error('加载任务列表失败');
      console.error('Failed to load tasks:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  // 加载单个任务详情
  const loadTaskDetail = useCallback(async (taskId: string) => {
    try {
      return await taskApi.getTaskDetails(taskId);
    } catch (error) {
      message.error('加载任务详情失败');
      console.error('Failed to load task detail:', error);
      throw error;
    }
  }, []);

  // 加载broker列表
  const loadBrokers = useCallback(async () => {
    try {
      const pageInfo = await mqttBrokerApi.getAllBrokers(true); // 只获取启用的broker
      return pageInfo.content;
    } catch (error) {
      message.error('加载Broker列表失败');
      console.error('Failed to load brokers:', error);
      throw error;
    }
  }, []);

  return {
    data,
    isLoading,
    error,
    loadTasks,
    loadTaskDetail,
    loadBrokers,
    refetch: loadTasks,
  };
};

export default useTaskData;