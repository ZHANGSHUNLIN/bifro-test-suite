import React, {useState} from 'react';
import {Table, Button, Space, Popconfirm, Tag, Input, Select} from 'antd';
import type { TableRowSelection } from 'antd/es/table/interface';
import {
    EyeOutlined,
    EditOutlined,
    DeleteOutlined,
    CheckCircleOutlined,
    DeploymentUnitOutlined,
    StopOutlined,
    SearchOutlined
} from '@ant-design/icons';
import {TaskStatusValues, TaskTypeValues} from '../../types/task';
import type {TaskListItem} from '../../types/task';

interface TaskListProps {
    tasks: TaskListItem[];
    groupSelectOptions?: { label: string; value: string }[];
    onViewDetail: (id: string, taskId: string) => void;
    onEdit: (task: TaskListItem) => void;
    onDelete: (id: string) => Promise<void>;
    onConfirm: (id: string) => Promise<void>;
    onAssign: (task: TaskListItem) => void;
    onStop: (id: string) => Promise<void>;
    onBatchDelete: (ids: string[]) => Promise<void>;
    selectedRowKeys?: React.Key[];
    onSelectChange?: (selectedRowKeys: React.Key[]) => void;
    onSearch?: (taskName: string, taskType: string | null, group: string) => void;
}

const TaskList: React.FC<TaskListProps> = ({
                                               tasks,
                                               groupSelectOptions = [],
                                               onViewDetail,
                                               onEdit,
                                               onDelete,
                                               onConfirm,
                                               onAssign,
                                               onStop,
                                               onBatchDelete: _onBatchDelete,
                                               selectedRowKeys = [],
                                               onSelectChange,
                                               onSearch
                                           }) => {
    // 过滤状态
    const [taskNameFilter, setTaskNameFilter] = useState<string>('');
    const [taskTypeFilter, setTaskTypeFilter] = useState<string | null>(null);
    const [groupFilter, setGroupFilter] = useState<string>('');

    // 处理搜索
    const handleSearch = () => {
        if (onSearch) {
            onSearch(taskNameFilter, taskTypeFilter, groupFilter);
        }
    };

    // 处理输入变化（按回车搜索）
    const handleTaskNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setTaskNameFilter(e.target.value);
    };

    const handleTaskNamePressEnter = () => {
        handleSearch();
    };

    const handleTaskTypeChange = (value: string | null) => {
        setTaskTypeFilter(value);
        // 任务类型变化时自动搜索
        if (onSearch) {
            onSearch(taskNameFilter, value, groupFilter);
        }
    };

    const handleGroupChange = (value: string) => {
        setGroupFilter(value);
        // 分组变化时自动搜索
        if (onSearch) {
            onSearch(taskNameFilter, taskTypeFilter, value);
        }
    };

    // 清除过滤
    const handleClearFilters = () => {
        setTaskNameFilter('');
        setTaskTypeFilter(null);
        setGroupFilter('');
        if (onSearch) {
            onSearch('', null, '');
        }
    };

    // 状态映射
    const statusMap: Record<string, { text: string; color: string }> = {
        [TaskStatusValues.INIT]: {text: '已创建', color: 'default'},
        [TaskStatusValues.ASSIGNED]: {text: '已分配', color: 'cyan'},
        [TaskStatusValues.START]: {text: '启动中', color: 'processing'},
        [TaskStatusValues.CONNECTING]: {text: '连接中', color: 'processing'},
        [TaskStatusValues.INIT_PUB_CLIENT]: {text: '初始化发布端', color: 'processing'},
        [TaskStatusValues.INIT_SUB_CLIENT]: {text: '初始化订阅端', color: 'processing'},
        [TaskStatusValues.ONGOING]: {text: '运行中', color: 'processing'},
        [TaskStatusValues.SHUTDOWN]: {text: '已完成', color: 'success'},
        [TaskStatusValues.STOPPED]: {text: '已停止', color: 'warning'},
    };

    const taskTypeMap: Record<string, { text: string; color: string }> = {
        [TaskTypeValues.CONN]: {text: '连接', color: 'blue'},
        [TaskTypeValues.PUBSUB]: {text: '发布/订阅', color: 'green'}
    };

    // 表格列配置
    const columns = [
        {
            title: '任务名称',
            dataIndex: 'taskName',
            key: 'taskName',
            width: 80,
            ellipsis: true,
        },
        {
            title: '任务ID',
            dataIndex: 'taskId',
            key: 'taskId',
            width: 100,
            ellipsis: true,
        },
        {
            title: '分组',
            dataIndex: 'group',
            key: 'group',
            width: 100,
            render: (group: string) => {
                const groupName = groupSelectOptions.find(opt => opt.value === group)?.label || group;
                return group ? <Tag color="blue">{groupName}</Tag> : <span>-</span>;
            },
        },
        {
            title: '任务类型',
            dataIndex: 'taskType',
            width: 100,
            key: 'taskType',
            render: (type: string) => (
                <Tag color={taskTypeMap[type]?.color || 'default'}>
                    {taskTypeMap[type]?.text || type}
                </Tag>
            ),
        },
        {
            title: '协议',
            dataIndex: 'protocol',
            key: 'protocol',
            width: 80,
        },
        {
            title: '服务器地址',
            dataIndex: 'brokers',
            width: 120,
            key: 'brokers',
            render: (brokers: Array<{ host: string, port: number, brokerId?: string, name?: string }>) => (
                <div>
                    {brokers.slice(0, 1).map((broker, index) => (
                        <div key={index} style={{fontSize: '12px'}}>
                            {broker.brokerId || broker.name || `${broker.host}:${broker.port}`}
                        </div>
                    ))}
                    {brokers.length > 1 && (
                        <div style={{fontSize: '12px', color: '#999'}}>
                            +{brokers.length - 2} 更多
                        </div>
                    )}
                </div>
            ),
        },
        {
            title: '客户端数量',
            dataIndex: 'totalClientCount',
            key: 'totalClientCount',
            width: 100,
            render: (count: number) => (
                <Tag color="blue">{count}</Tag>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 100,
            render: (status: string) => (
                <Tag color={statusMap[status]?.color || 'default'}>
                    {statusMap[status]?.text || status}
                </Tag>
            ),
        },
        {
            title: '操作',
            key: 'action',
            width: 200,
            render: (_: unknown, record: TaskListItem) => {
                const status = record.status;
                const isRunning = status === TaskStatusValues.START
                    || status === TaskStatusValues.CONNECTING
                    || status === TaskStatusValues.INIT_PUB_CLIENT
                    || status === TaskStatusValues.INIT_SUB_CLIENT
                    || status === TaskStatusValues.ONGOING;

                return (
                    <Space.Compact size="small">
                        <Button
                            type="link"
                            icon={<EyeOutlined/>}
                            onClick={() => onViewDetail(record.id, record.taskId)}
                        >
                            详情
                        </Button>
                        {status === TaskStatusValues.INIT && (
                            <Button
                                type="link"
                                icon={<EditOutlined/>}
                                onClick={() => onEdit(record)}
                            >
                                编辑
                            </Button>
                        )}
                        {(status === TaskStatusValues.INIT || status === TaskStatusValues.ASSIGNED) && (
                            <Button
                                type="link"
                                icon={<DeploymentUnitOutlined/>}
                                onClick={() => onAssign(record)}
                            >
                                {status === TaskStatusValues.ASSIGNED ? '重新分配' : '分配'}
                            </Button>
                        )}
                        {status === TaskStatusValues.ASSIGNED && (
                            <Button
                                type="link"
                                icon={<CheckCircleOutlined/>}
                                onClick={() => onConfirm(record.id)}
                            >
                                确认
                            </Button>
                        )}
                        {isRunning && (
                            <Popconfirm
                                title="确定要停止这个任务吗？"
                                onConfirm={() => onStop(record.id)}
                                okText="确定"
                                cancelText="取消"
                            >
                                <Button type="link" icon={<StopOutlined/>}>
                                    停止
                                </Button>
                            </Popconfirm>
                        )}
                        {!isRunning && (
                            <Popconfirm
                                title="确定要删除这个任务吗？"
                                onConfirm={() => onDelete(record.id)}
                                okText="确定"
                                cancelText="取消"
                            >
                                <Button type="link" danger icon={<DeleteOutlined/>}>
                                    删除
                                </Button>
                            </Popconfirm>
                        )}
                    </Space.Compact>
                );
            },
        },
    ];

    // 表格行选择配置
    // 允许选择的状态：INIT, STOPPED, SHUTDOWN
    const canSelectStatuses = [
        TaskStatusValues.INIT,
        TaskStatusValues.STOPPED,
        TaskStatusValues.SHUTDOWN
    ];

    const rowSelection: TableRowSelection<TaskListItem> = {
        selectedRowKeys,
        onChange: onSelectChange,
        getCheckboxProps: (record: TaskListItem) => ({
            disabled: !canSelectStatuses.includes(record.status as any),
            title: !canSelectStatuses.includes(record.status as any) ? '该状态不允许删除' : undefined,
        }),
    };

    return (
        <div>
            <div style={{marginBottom: 16, display: 'flex', gap: 12}}>
                <Input
                    placeholder="搜索任务名称"
                    value={taskNameFilter}
                    onChange={handleTaskNameChange}
                    onPressEnter={handleTaskNamePressEnter}
                    style={{width: 200}}
                    allowClear
                    prefix={<SearchOutlined style={{color: '#bfbfbf'}}/>}
                />
                <Select
                    placeholder="选择分组"
                    value={groupFilter}
                    onChange={handleGroupChange}
                    style={{width: 150}}
                    options={groupSelectOptions}
                />
                <Select
                    placeholder="选择任务类型"
                    value={taskTypeFilter}
                    onChange={handleTaskTypeChange}
                    style={{width: 150}}
                    allowClear
                    options={[
                        {label: '连接', value: TaskTypeValues.CONN},
                        {label: '发布/订阅', value: TaskTypeValues.PUBSUB}
                    ]}
                />
                <Button type="primary" icon={<SearchOutlined/>} onClick={handleSearch}>搜索</Button>
                {(taskNameFilter || taskTypeFilter || groupFilter) && (
                    <Button onClick={handleClearFilters}>清除过滤</Button>
                )}
            </div>
            <Table
                columns={columns}
                dataSource={tasks}
                rowKey="id"
                rowSelection={rowSelection}
                pagination={{
                    pageSize: 10,
                    showSizeChanger: true,
                    showQuickJumper: true,
                    showTotal: (total) => `共 ${total} 条记录`,
                }}
            />
        </div>
    );
};

export default TaskList;