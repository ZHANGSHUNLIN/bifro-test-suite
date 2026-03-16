import { useCallback } from 'react';
import { message } from 'antd';
import taskApi from '../../../services/taskApi';
import type { TaskRequest } from '../../../types/task';

interface UseTaskMutationOptions {
  onSuccess?: () => void;
  onError?: (error: Error) => void;
}

export const useTaskMutation = (onSuccessCallback?: () => void, options?: UseTaskMutationOptions) => {
  const { onSuccess, onError } = options || {};

  // 处理所有操作的成功/失败
  const handleOperation = useCallback(async <T>(
    operation: () => Promise<T>,
    successMessage: string,
    errorMessage: string
  ): Promise<T | undefined> => {
    try {
      const result = await operation();
      message.success(successMessage);
      onSuccess?.();
      onSuccessCallback?.();
      return result;
    } catch (error) {
      console.error(`${errorMessage}:`, error);
      message.error(`${errorMessage}: ${error instanceof Error ? error.message : String(error)}`);
      onError?.(error as Error);
      return undefined;
    }
  }, [onSuccess, onSuccessCallback, onError]);

  // 处理操作但只关心成功/失败，不关心返回值
  const handleOperationVoid = useCallback(async (
    operation: () => Promise<any>,
    successMessage: string,
    errorMessage: string
  ): Promise<void> => {
    try {
      await operation();
      message.success(successMessage);
      onSuccess?.();
      onSuccessCallback?.();
    } catch (error) {
      console.error(`${errorMessage}:`, error);
      message.error(`${errorMessage}: ${error instanceof Error ? error.message : String(error)}`);
      onError?.(error as Error);
    }
  }, [onSuccess, onSuccessCallback, onError]);

  // 添加任务
  const handleAdd = useCallback(async (taskRequest: TaskRequest) => {
    return handleOperation(
      () => taskApi.addTask(taskRequest),
      '添加成功',
      '添加任务失败'
    );
  }, [handleOperation]);

  // 更新任务
  const handleUpdate = useCallback(async (taskId: string | undefined, taskRequest: TaskRequest) => {
    if (!taskId) return undefined;
    return handleOperation(
      () => taskApi.updateTask(taskId, taskRequest),
      '更新成功',
      '更新任务失败'
    );
  }, [handleOperation]);

  // 删除任务
  const handleDelete = useCallback(async (taskId: string) => {
    await handleOperationVoid(
      () => taskApi.deleteTask(taskId),
      '删除任务已提交',
      '删除失败'
    );
  }, [handleOperationVoid]);

  // 确认任务
  const handleConfirm = useCallback(async (taskId: string) => {
    await handleOperationVoid(
      () => taskApi.confirmTask(taskId),
      '任务确认成功',
      '任务确认失败'
    );
  }, [handleOperationVoid]);

  // 分配任务
  const handleAssign = useCallback(async (taskId: string) => {
    await handleOperationVoid(
      () => taskApi.assignTask(taskId),
      '任务分配成功',
      '任务分配失败'
    );
  }, [handleOperationVoid]);

  // 停止任务
  const handleStop = useCallback(async (taskId: string) => {
    await handleOperationVoid(
      () => taskApi.stopTask(taskId),
      '任务停止已提交',
      '停止任务失败'
    );
  }, [handleOperationVoid]);

  return {
    handleAdd,
    handleUpdate,
    handleDelete,
    handleConfirm,
    handleAssign,
    handleStop,
  };
};

export default useTaskMutation;