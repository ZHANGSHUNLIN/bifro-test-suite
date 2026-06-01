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

import {api} from '../../../utils/request';
import type {
    NodeTaskAllocationVO,
    PageInfo,
    StateHistoryItem,
    TaskBasicInfoResponse,
    TaskConfig,
    TaskDiagnosticsResponse,
    TaskDetailResponse,
    TaskListItem,
    TaskLogSummaryResponse,
    TaskReportResponse,
    TaskStatisticsResponse,
    TaskSubTasksResponse
} from '../domain';

export const taskApi = {
    getAllTasks: (taskName?: string, taskType?: string, group?: string, status?: string, pageNum: number = 1, pageSize: number = 20) => {
        const params: Record<string, string | number | boolean> = {pageNum, pageSize};
        if (taskName) params.taskName = taskName;
        if (taskType) params.taskType = taskType;
        if (group) params.group = group;
        if (status) params.status = status;
        return api.get<PageInfo<TaskListItem>>('/task/list', {params});
    },
    getTaskDetails: (id: string) => {
        return api.get<TaskDetailResponse>('/task/:id', {params: {id}});
    },
    getTaskBasicInfo: (id: string) => {
        return api.get<TaskBasicInfoResponse>('/task/:id/basic', {params: {id}});
    },
    getTaskConfig: (id: string) => {
        return api.get<TaskConfig>('/task/:id/config', {params: {id}});
    },
    getTaskStatistics: (id: string) => {
        return api.get<TaskStatisticsResponse>('/task/:id/statistics', {params: {id}});
    },
    getTaskSubTasks: (id: string) => {
        return api.get<TaskSubTasksResponse>('/task/:id/subtasks', {params: {id}});
    },
    addTask: (taskRequest: any) => {
        return api.post<TaskConfig>('/task', taskRequest);
    },
    updateTask: (id: string, taskRequest: any) => {
        return api.put<TaskConfig>('/task/:id', taskRequest, {params: {id}});
    },
    confirmTask: (id: string) => {
        return api.post<TaskDetailResponse>('/task/:id/confirmTask', undefined, {params: {id}});
    },
    assignTask: (taskId: string, allocationRequest?: NodeTaskAllocationVO) => {
        return api.post<TaskConfig>('/task/assign/:taskId', allocationRequest, {params: {taskId}});
    },
    calculateNodeTaskAllocation: (taskId: string) => {
        return api.post<NodeTaskAllocationVO>('/task/calculate/:taskId', null, {params: {taskId}});
    },
    deleteTask: (id: string) => {
        return api.delete<TaskDetailResponse>('/task/:id', {params: {id}});
    },
    batchDeleteTask: (ids: string[]) => {
        return api.deleteWithBody<string>('/task/batch', ids);
    },
    stopTask: (id: string) => {
        return api.post<string>('/task/stop/:id', undefined, {params: {id}});
    },
    getTemplates: () => {
        return api.get<Array<{ value: string; label: string; type: string }>>('/task/templates');
    },
    getTaskReport: (id: string) => {
        return api.get<TaskReportResponse>(`/task/${id}/report`);
    },
    getStateHistory: (id: string, nodeId?: string) => {
        const url = nodeId ? `/task/${id}/state-history?nodeId=${nodeId}` : `/task/${id}/state-history`;
        return api.get<StateHistoryItem[]>(url);
    },
    getTaskDiagnostics: (id: string) => {
        return api.get<TaskDiagnosticsResponse>('/task/:id/diagnostics', {params: {id}});
    },
    getTaskLogSummary: (id: string, lines?: number) => {
        const params: Record<string, string | number> = {id};
        if (lines !== undefined) params.lines = lines;
        return api.get<TaskLogSummaryResponse>('/task/:id/log-summary', {params});
    },
};

export default taskApi;
