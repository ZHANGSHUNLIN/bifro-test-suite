/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {useCallback} from 'react';
import {message} from 'antd';
import {useTranslation} from 'react-i18next';
import {taskApi} from '../api';
import type {TaskRequest} from '../domain';

interface UseTaskMutationOptions {
    onSuccess?: () => void;
    onError?: (error: Error) => void;
}

export const useTaskMutation = (onSuccessCallback?: () => void, options?: UseTaskMutationOptions) => {
    const {onSuccess, onError} = options || {};
    const {t} = useTranslation();

    // Handle success/failure for all operations
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

    // Handle operation but only care about success/failure, not return value
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

    // Add task
    const handleAdd = useCallback(async (taskRequest: TaskRequest) => {
        return handleOperation(
            () => taskApi.addTask(taskRequest),
            t('task.msg.addSuccess'),
            t('task.msg.addFailed')
        );
    }, [handleOperation, t]);

    // Update task
    const handleUpdate = useCallback(async (taskId: string | undefined, taskRequest: TaskRequest) => {
        if (!taskId) return undefined;
        return handleOperation(
            () => taskApi.updateTask(taskId, taskRequest),
            t('task.msg.updateSuccess'),
            t('task.msg.updateFailed')
        );
    }, [handleOperation, t]);

    // delete task
    const handleDelete = useCallback(async (taskId: string) => {
        await handleOperationVoid(
            () => taskApi.deleteTask(taskId),
            t('task.msg.deleteSubmitted'),
            t('task.msg.deleteFailed')
        );
    }, [handleOperationVoid, t]);

    // confirm task
    const handleConfirm = useCallback(async (taskId: string) => {
        await handleOperationVoid(
            () => taskApi.confirmTask(taskId),
            t('task.msg.confirmSuccess'),
            t('task.msg.confirmFailed')
        );
    }, [handleOperationVoid, t]);

    // assign task
    const handleAssign = useCallback(async (taskId: string) => {
        await handleOperationVoid(
            () => taskApi.assignTask(taskId),
            t('task.msg.assignSuccess'),
            t('task.msg.assignFailed')
        );
    }, [handleOperationVoid, t]);

    // stop task
    const handleStop = useCallback(async (taskId: string) => {
        await handleOperationVoid(
            () => taskApi.stopTask(taskId),
            t('task.msg.stopSubmitted'),
            t('task.msg.stopFailed')
        );
    }, [handleOperationVoid, t]);

    // Batch delete tasks
    const handleBatchDelete = useCallback(async (taskIds: string[]) => {
        if (!taskIds || taskIds.length === 0) {
            message.warning(t('task.msg.batchDeleteEmpty'));
            return;
        }
        await handleOperationVoid(
            () => taskApi.batchDeleteTask(taskIds),
            t('task.msg.batchDeleteSubmitted'),
            t('task.msg.batchDeleteFailed')
        );
    }, [handleOperationVoid, t]);

    return {
        handleAdd,
        handleUpdate,
        handleDelete,
        handleConfirm,
        handleAssign,
        handleStop,
        handleBatchDelete,
    };
};

export default useTaskMutation;
