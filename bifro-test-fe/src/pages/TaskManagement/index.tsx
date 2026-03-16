import React, {useState, useEffect} from 'react';
import {Card, Spin, Button, Space, Typography} from 'antd';
import {PlusOutlined, ReloadOutlined} from '@ant-design/icons';
import TaskList from './TaskList';
import TaskEditor from './TaskEditor';
import TaskDetailModal from './TaskDetailModal';
import TaskAllocationModal from './TaskAllocationModal';
import {useTaskData, useTaskMutation} from './hooks';
import type {TaskListItem, TaskRequest} from '../../types/task';

const {Title} = Typography;

const TaskManagement: React.FC = () => {
    // 状态管理
    const [isEditorVisible, setIsEditorVisible] = useState(false);
    const [isDetailModalVisible, setIsDetailModalVisible] = useState(false);
    const [isAllocationModalVisible, setIsAllocationModalVisible] = useState(false);
    const [editingTask, setEditingTask] = useState<TaskListItem | null>(null);
    const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [allocatingTask, setAllocatingTask] = useState<TaskListItem | null>(null);

    // 数据获取hooks
    const {data: tasks, isLoading, refetch: loadTasks} = useTaskData();
    const {handleAdd, handleUpdate, handleDelete, handleConfirm, handleAssign, handleStop} = useTaskMutation(loadTasks);

    // 初始化加载
    useEffect(() => {
        loadTasks();
    }, [loadTasks]);

    // 处理查看详情
    const handleViewDetail = (id: string, taskId: string) => {
        setSelectedTaskId(taskId);
        setSelectedId(id);
        setIsDetailModalVisible(true);
    };

    // 处理编辑任务
    const handleEdit = async (task: TaskListItem) => {
        setEditingTask(task);
        setIsEditorVisible(true);
    };

    // 处理添加任务
    const handleAddClick = () => {
        setEditingTask(null);
        setIsEditorVisible(true);
    };

    // 处理刷新任务
    const handleManualRefresh = () => {
        loadTasks();
    };

    // 处理分配任务（打开分配弹窗）
    const handleAssignClick = (task: TaskListItem) => {
        setAllocatingTask(task);
        setIsAllocationModalVisible(true);
    };

    // 统一的onOk处理函数
    const handleEditorOk = async (taskId: string | undefined, taskRequest: TaskRequest) => {
        try {
            if (editingTask) {
                // 更新现有任务
                const result = await handleUpdate(taskId, taskRequest);
                if (result !== undefined) {
                    // 更新成功，关闭模态框
                    setIsEditorVisible(false);
                    setEditingTask(null);
                }
            } else {
                // 添加新任务
                const result = await handleAdd(taskRequest);
                if (result !== undefined) {
                    // 添加成功，关闭模态框
                    setIsEditorVisible(false);
                }
            }
        } catch (error) {
            console.error('任务操作失败:', error);
            // 失败时不关闭模态框，让用户看到错误信息并可以修正
        }
    };

    return (
        <div>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24}}>
                <Title level={2}>任务管理</Title>
                <Space>
                    <Button icon={<ReloadOutlined/>} onClick={handleManualRefresh}>
                        刷新
                    </Button>
                    <Button type="primary" icon={<PlusOutlined/>} onClick={handleAddClick}>
                        添加任务
                    </Button>
                </Space>
            </div>

            <Card>
                <Spin spinning={isLoading}>
                    <TaskList
                        tasks={tasks || []}
                        onViewDetail={handleViewDetail}
                        onEdit={handleEdit}
                        onDelete={handleDelete}
                        onConfirm={handleConfirm}
                        onAssign={handleAssignClick}
                        onStop={handleStop}
                    />
                </Spin>
            </Card>

            {/* 任务编辑器 */}
            <TaskEditor
                visible={isEditorVisible}
                editingTask={editingTask}
                onCancel={() => {
                    setIsEditorVisible(false);
                    setEditingTask(null);
                }}
                onOk={handleEditorOk}
            />

            {/* 任务详情模态框 */}
            <TaskDetailModal
                visible={isDetailModalVisible}
                id={selectedId || ''}
                taskId={selectedTaskId}
                onClose={() => {
                    setIsDetailModalVisible(false);
                    setSelectedTaskId(null);
                }}
            />

            {/* 任务分配弹窗 */}
            <TaskAllocationModal
                visible={isAllocationModalVisible}
                taskId={allocatingTask?.id || ''}
                taskName={allocatingTask?.taskName}
                onCancel={() => {
                    setIsAllocationModalVisible(false);
                    setAllocatingTask(null);
                }}
                onSuccess={() => {
                    setIsAllocationModalVisible(false);
                    setAllocatingTask(null);
                }}
            />
        </div>
    );
};

export default TaskManagement;