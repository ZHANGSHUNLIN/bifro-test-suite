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

import {useCallback, useState} from 'react';
import {message} from 'antd';
import {useTranslation} from 'react-i18next';
import {mqttBrokerApi, taskApi} from '../api';
import type {TaskListItem} from '../domain';

export const useTaskData = () => {
    const {t} = useTranslation();
    const [data, setData] = useState<TaskListItem[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    // Load task list
    const loadTasks = useCallback(async (taskName?: string, taskType?: string, group?: string, status?: string) => {
        setIsLoading(true);
        setError(null);
        try {
            // Backend returns ApiResponse<PageInfo<TaskListVO>>, request.ts extracts data part
            const pageInfo = await taskApi.getAllTasks(taskName, taskType, group, status);
            const taskListItems: TaskListItem[] = pageInfo.content.map((config: any) => ({
                id: config.id || '',
                taskId: config.taskId || '',
                taskName: config.taskName,
                taskType: config.taskType,
                protocol: config.protocol,
                group: config.group,
                brokers: config.brokers || config.hosts, // backward compat
                totalClientCount: config.totalClientCount || 0,
                status: config.taskWorkStage,
                createTimeMs: config.createTimeMs,
                createTime: config.createTime,
            }));
            setData(taskListItems);
        } catch (error) {
            setError(error as Error);
            message.error(t('task.msg.loadListFailed'));
            console.error('Failed to load tasks:', error);
        } finally {
            setIsLoading(false);
        }
    }, []);

    // Load full task config for edit/copy flows
    const loadTaskConfig = useCallback(async (taskId: string) => {
        try {
            return await taskApi.getTaskConfig(taskId);
        } catch (error) {
            message.error(t('task.msg.loadDetailFailed'));
            console.error('Failed to load task config:', error);
            throw error;
        }
    }, []);

    // Load task basic info
    const loadTaskBasicInfo = useCallback(async (taskId: string) => {
        try {
            return await taskApi.getTaskBasicInfo(taskId);
        } catch (error) {
            message.error(t('task.msg.loadBasicFailed'));
            console.error('Failed to load task basic info:', error);
            throw error;
        }
    }, []);

    // Load task statistics
    const loadTaskStatistics = useCallback(async (taskId: string) => {
        try {
            return await taskApi.getTaskStatistics(taskId);
        } catch (error) {
            message.error(t('task.msg.loadStatsFailed'));
            console.error('Failed to load task statistics:', error);
            throw error;
        }
    }, []);

    // Load task subtask info
    const loadTaskSubTasks = useCallback(async (taskId: string) => {
        try {
            return await taskApi.getTaskSubTasks(taskId);
        } catch (error) {
            message.error(t('task.msg.loadSubtasksFailed'));
            console.error('Failed to load task subtasks:', error);
            throw error;
        }
    }, []);

    // Load broker list
    const loadBrokers = useCallback(async () => {
        try {
            const pageInfo = await mqttBrokerApi.getAllBrokers(true); // get enabled brokers only
            return pageInfo.content;
        } catch (error) {
            message.error(t('task.msg.loadBrokerFailed'));
            console.error('Failed to load brokers:', error);
            throw error;
        }
    }, []);

    return {
        data,
        isLoading,
        error,
        loadTasks,
        loadTaskConfig,
        loadTaskBasicInfo,
        loadTaskStatistics,
        loadTaskSubTasks,
        loadBrokers,
        refetch: loadTasks,
    };
};

export default useTaskData;
