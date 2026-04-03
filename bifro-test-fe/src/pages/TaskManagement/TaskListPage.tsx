import React, {useState, useEffect} from 'react';
import {Card, Spin, Button, Space, Typography, Popconfirm, Select} from 'antd';
import {PlusOutlined, ReloadOutlined, DeleteOutlined, SettingOutlined} from '@ant-design/icons';
import TaskList from './TaskList';
import TaskEditor from './TaskEditor';
import TaskDetailModal from './TaskDetailModal';
import TaskAllocationModal from './TaskAllocationModal';
import {useTaskData, useTaskMutation} from './hooks';
import type {TaskListItem, TaskRequest} from '../../types/task';
import taskGroupApi from '../../services/taskGroupApi';
import type {TaskGroup} from '../../types/taskGroup';
import {useNavigate} from 'react-router-dom';

const {Title} = Typography;

const TaskListPage: React.FC = () => {
    const navigate = useNavigate();
    // 状态管理
    const [isEditorVisible, setIsEditorVisible] = useState(false);
    const [isDetailModalVisible, setIsDetailModalVisible] = useState(false);
    const [isAllocationModalVisible, setIsAllocationModalVisible] = useState(false);
    const [editingTask, setEditingTask] = useState<TaskListItem | null>(null);
    const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [allocatingTask, setAllocatingTask] = useState<TaskListItem | null>(null);
    const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
    const [groupSelectOptions, setGroupSelectOptions] = useState<{ label: string; value: string }[]>([]);
    const [selectedGroup, setSelectedGroup] = useState<string>('');

    // 数据获取hooks
    const {data: tasks, isLoading, refetch: loadTasks} = useTaskData();
    const {handleAdd, handleUpdate, handleDelete, handleConfirm, handleStop, handleBatchDelete} = useTaskMutation(loadTasks);

    // 加载分组选项（用于下拉选择）
    const loadGroupSelectOptions = async () => {
        try {
            // 先确保默认分组存在
            const defaultGroup = await taskGroupApi.getOrCreateDefaultGroup();
            setSelectedGroup(defaultGroup.id);

            // 获取所有分组
            const allGroups = await taskGroupApi.getAllGroupsForSelect();

            // 确保"默认分组"在列表第一位
            const otherGroups = allGroups.filter((g: TaskGroup) => g.name !== '默认分组');
            const sortedGroups = [defaultGroup, ...otherGroups];

            const options = sortedGroups.map((g: TaskGroup) => ({
                label: g.name,
                value: g.id
            }));
            setGroupSelectOptions(options);
            loadTasks(undefined, undefined, defaultGroup.id);
        } catch (error) {
            console.error('加载分组选项失败:', error);
        }
    };

    // 初始化加载
    useEffect(() => {
        loadGroupSelectOptions();
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

    // 处理搜索
    const handleSearch = (taskName: string, taskType: string | null, group: string) => {
        loadTasks(taskName || undefined, taskType || undefined, group || undefined);
    };

    // 处理分组变化
    const handleGroupChange = (group: string) => {
        setSelectedGroup(group);
        loadTasks(undefined, undefined, group || undefined);
    };

    // 处理分配任务（打开分配弹窗）
    const handleAssignClick = (task: TaskListItem) => {
        setAllocatingTask(task);
        setIsAllocationModalVisible(true);
    };

    // 处理批量删除
    const handleBatchDeleteClick = () => {
        if (selectedRowKeys.length === 0) {
            return;
        }
        handleBatchDelete(selectedRowKeys as string[]).then(() => {
            setSelectedRowKeys([]);
        });
    };

    // 处理选中变化
    const handleSelectChange = (keys: React.Key[]) => {
        setSelectedRowKeys(keys);
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
                    <Select
                        placeholder="选择分组"
                        style={{ width: 150 }}
                        value={selectedGroup}
                        onChange={handleGroupChange}
                        options={groupSelectOptions}
                        dropdownRender={(menu) => (
                            <>
                                {menu}
                                <Button
                                    type="link"
                                    icon={<SettingOutlined />}
                                    style={{ fontSize: 12, marginLeft: 8 }}
                                    onClick={() => navigate('/task-groups')}
                                >
                                    管理分组
                                </Button>
                            </>
                        )}
                    />
                    {selectedRowKeys.length > 0 && (
                        <Popconfirm
                            title={`确定要删除选中的 ${selectedRowKeys.length} 个任务吗？`}
                            onConfirm={handleBatchDeleteClick}
                            okText="确定"
                            cancelText="取消"
                        >
                            <Button danger icon={<DeleteOutlined/>}>
                                批量删除 ({selectedRowKeys.length})
                            </Button>
                        </Popconfirm>
                    )}
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
                        groupSelectOptions={groupSelectOptions}
                        onViewDetail={handleViewDetail}
                        onEdit={handleEdit}
                        onDelete={handleDelete}
                        onConfirm={handleConfirm}
                        onAssign={handleAssignClick}
                        onStop={handleStop}
                        onBatchDelete={handleBatchDelete}
                        selectedRowKeys={selectedRowKeys}
                        onSelectChange={handleSelectChange}
                        onSearch={handleSearch}
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
                    loadTasks();
                }}
            />
        </div>
    );
};

export default TaskListPage;
