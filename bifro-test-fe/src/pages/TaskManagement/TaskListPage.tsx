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

import React, {useCallback, useEffect, useState} from 'react';
import {Card, message, Spin} from 'antd';
import {useTranslation} from 'react-i18next';
import TaskList from './TaskList';
import TaskEditor from './TaskEditor';
import TaskDetailModal from './TaskDetailModal';
import TaskAllocationModal from './TaskAllocationModal';
import TaskReportModal from '../../components/TaskReportModal';
import {useTaskData, useTaskMutation} from '../../features/task/model';
import type {TaskListItem, TaskRequest} from '../../features/task';
import {generateCopyTaskName} from '../../utils/taskUtils';
import {taskApi} from '../../features/task';
import groupApi from '../../features/group';
import type {MqttGroup} from '../../features/group';

const TaskListPage: React.FC = () => {
    const {t} = useTranslation();
    // State management
    const [isEditorVisible, setIsEditorVisible] = useState(false);
    const [isDetailModalVisible, setIsDetailModalVisible] = useState(false);
    const [isAllocationModalVisible, setIsAllocationModalVisible] = useState(false);
    const [editingTask, setEditingTask] = useState<TaskListItem | null>(null);
    const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
    const [allocatingTask, setAllocatingTask] = useState<TaskListItem | null>(null);
    const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
    const [groupSelectOptions, setGroupSelectOptions] = useState<{ label: string; value: string }[]>([]);
    const [currentGroupId, setCurrentGroupId] = useState<string | undefined>(undefined);
    const [reportTask, setReportTask] = useState<TaskListItem | null>(null);

    // Data fetching hooks
    const {data: tasks, isLoading, refetch: loadTasks} = useTaskData();
    // Wrap refetch to use current group filter on refresh
    const refreshTasks = useCallback(() => {
        loadTasks(undefined, undefined, currentGroupId);
    }, [loadTasks, currentGroupId]);
    const {
        handleAdd,
        handleUpdate,
        handleDelete,
        handleConfirm,
        handleStop,
        handleBatchDelete: handleBatchDeleteBase
    } = useTaskMutation(refreshTasks);

    // Wrap batch delete to clear selection on success
    const handleBatchDelete = useCallback(async (taskIds: string[]) => {
        await handleBatchDeleteBase(taskIds);
        // Clear selection regardless of success/failure (user re-selects on failure)
        setSelectedRowKeys([]);
    }, [handleBatchDeleteBase]);

    // Load group options (for dropdown)
    const loadGroupSelectOptions = async () => {
        try {
            const allGroups = await groupApi.getAllGroupsForSelect('BROKER');
            const options = allGroups.map((g: MqttGroup) => ({
                label: g.name,
                value: g.id
            }));
            setGroupSelectOptions(options);

            if (options.length > 0) {
                const nextGroupId = currentGroupId && options.some((option) => option.value === currentGroupId)
                    ? currentGroupId
                    : options[0].value;
                setCurrentGroupId(nextGroupId);
                loadTasks(undefined, undefined, nextGroupId);
            } else {
                setCurrentGroupId(undefined);
                loadTasks(undefined, undefined, '__NO_TASK_GROUP__');
            }
        } catch (error) {
            console.error('Failed to load group options:', error);
        }
    };

    // Initial load
    useEffect(() => {
        loadGroupSelectOptions();
    }, []);

    // Handle view details
    const handleViewDetail = (_id: string, taskId: string) => {
        setSelectedTaskId(taskId);
        setIsDetailModalVisible(true);
    };

    // Handle edit task
    const handleEdit = async (task: TaskListItem) => {
        setEditingTask(task);
        setIsEditorVisible(true);
    };

    // Handle copy task
    const handleCopy = async (task: TaskListItem) => {
        try {
            const [taskConfig, basicInfo] = await Promise.all([
                taskApi.getTaskConfig(task.id),
                taskApi.getTaskBasicInfo(task.id),
            ]);
            if (taskConfig) {
                const newTaskName = generateCopyTaskName(task.taskName || t('task.msg.unnamed'));
                const brokers = basicInfo.brokers?.length ? basicInfo.brokers : (taskConfig.brokers || task.brokers);

                // Build copy data (empty id indicates new task)
                // Copy complete task config including all params from the explicit config endpoint.
                const copiedData: TaskListItem = {
                    id: '',  // empty id indicates copy mode
                    taskId: '',
                    taskName: newTaskName,
                    taskType: taskConfig.taskType || task.taskType,
                    protocol: taskConfig.protocol || task.protocol,
                    group: taskConfig.group || basicInfo.group || task.group,
                    brokers,
                    totalClientCount: taskConfig.totalClientCount || task.totalClientCount,
                    status: 'INIT',
                    taskConfig,
                };

                setEditingTask(copiedData);
                setIsEditorVisible(true);
            }
        } catch (error) {
            message.error(t('task.msg.copyFailed') + ': ' + (error instanceof Error ? error.message : t('common.unknown')));
        }
    };

    // Handle add task
    const handleAddClick = () => {
        if (groupSelectOptions.length === 0) {
            message.warning(t('task.msg.createTaskGroupFirst'));
            return;
        }
        setEditingTask(null);
        setIsEditorVisible(true);
    };

    // Handle search
    const handleSearch = (taskName: string, taskType: string | null, group: string, status: string | null) => {
        const effectiveGroup = group || groupSelectOptions[0]?.value || '';
        setCurrentGroupId(effectiveGroup || undefined);
        if (!effectiveGroup) {
            loadTasks(undefined, undefined, '__NO_TASK_GROUP__');
            return '';
        }
        loadTasks(taskName || undefined, taskType || undefined, effectiveGroup, status || undefined);
        return effectiveGroup;
    };

    // Handle assign task (open allocation modal)
    const handleAssignClick = (task: TaskListItem) => {
        setAllocatingTask(task);
        setIsAllocationModalVisible(true);
    };

    // Handle selection change
    const handleSelectChange = (keys: React.Key[]) => {
        setSelectedRowKeys(keys);
    };

    // Unified onOk handler
    const handleEditorOk = async (taskId: string | undefined, taskRequest: TaskRequest) => {
        try {
            // Check if copy mode (editingTask.id is empty)
            if (editingTask && editingTask.id === '') {
                // Copy mode: call handleAdd
                const result = await handleAdd(taskRequest);
                if (result !== undefined) {
                    setIsEditorVisible(false);
                    setEditingTask(null);
                    if (currentGroupId) {
                        loadTasks(undefined, undefined, currentGroupId);
                    } else {
                        loadGroupSelectOptions();
                    }
                }
            } else if (editingTask) {
                // Update existing task
                const result = await handleUpdate(taskId, taskRequest);
                if (result !== undefined) {
                    setIsEditorVisible(false);
                    setEditingTask(null);
                    if (currentGroupId) {
                        loadTasks(undefined, undefined, currentGroupId);
                    } else {
                        loadGroupSelectOptions();
                    }
                }
            } else {
                // Normal add mode
                const result = await handleAdd(taskRequest);
                if (result !== undefined) {
                    setIsEditorVisible(false);
                    if (currentGroupId) {
                        loadTasks(undefined, undefined, currentGroupId);
                    } else {
                        loadGroupSelectOptions();
                    }
                }
            }
        } catch (error) {
            console.error('Task operation failed:', error);
        }
    };

    return (
        <div>
            <Card>
                <Spin spinning={isLoading}>
                    <TaskList
                        tasks={tasks || []}
                        groupSelectOptions={groupSelectOptions}
                        initialGroupFilter={currentGroupId}
                        onViewDetail={handleViewDetail}
                        onEdit={handleEdit}
                        onCopy={handleCopy}
                        onDelete={handleDelete}
                        onConfirm={handleConfirm}
                        onAssign={handleAssignClick}
                        onStop={handleStop}
                        onBatchDelete={handleBatchDelete}
                        selectedRowKeys={selectedRowKeys}
                        onSelectChange={handleSelectChange}
                        onSearch={handleSearch}
                        onRefresh={refreshTasks}
                        onAdd={handleAddClick}
                        onReport={(task) => setReportTask(task)}
                    />
                </Spin>
            </Card>

            {/* Task editor */}
            <TaskEditor
                visible={isEditorVisible}
                editingTask={editingTask}
                initialGroup={currentGroupId}
                onCancel={() => {
                    setIsEditorVisible(false);
                    setEditingTask(null);
                }}
                onOk={handleEditorOk}
            />

            {/* Task details modal */}
            <TaskDetailModal
                visible={isDetailModalVisible}
                taskId={selectedTaskId}
                onClose={() => {
                    setIsDetailModalVisible(false);
                    setSelectedTaskId(null);
                }}
            />

            {/* Task allocation modal */}
            <TaskAllocationModal
                visible={isAllocationModalVisible}
                taskId={allocatingTask?.id || ''}
                taskName={allocatingTask?.taskName}
                taskStatus={allocatingTask?.status}
                onCancel={() => {
                    setIsAllocationModalVisible(false);
                    setAllocatingTask(null);
                }}
                onSuccess={() => {
                    setIsAllocationModalVisible(false);
                    setAllocatingTask(null);
                    if (currentGroupId) {
                        loadTasks(undefined, undefined, currentGroupId);
                    } else {
                        loadGroupSelectOptions();
                    }
                }}
            />

            {/* Test report modal */}
            <TaskReportModal
                open={!!reportTask}
                taskId={reportTask?.taskId || ''}
                taskName={reportTask?.taskName}
                taskType={reportTask?.taskType}
                onClose={() => setReportTask(null)}
            />
        </div>
    );
};

export default TaskListPage;
