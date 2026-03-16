import React from 'react';
import {Table, Button, Space, Popconfirm, Tag} from 'antd';
import {
    EyeOutlined,
    EditOutlined,
    DeleteOutlined,
    CheckCircleOutlined,
    DeploymentUnitOutlined,
    StopOutlined
} from '@ant-design/icons';
import {TaskStatusValues, TaskTypeValues} from '../../types/task';
import type {TaskListItem} from '../../types/task';

interface TaskListProps {
    tasks: TaskListItem[];
    onViewDetail: (id: string, taskId: string) => void;
    onEdit: (task: TaskListItem) => void;
    onDelete: (id: string) => Promise<void>;
    onConfirm: (id: string) => Promise<void>;
    onAssign: (task: TaskListItem) => void;
    onStop: (id: string) => Promise<void>;
}

const TaskList: React.FC<TaskListProps> = ({
                                               tasks,
                                               onViewDetail,
                                               onEdit,
                                               onDelete,
                                               onConfirm,
                                               onAssign,
                                               onStop
                                           }) => {
    // 状态映射
    const statusMap: Record<string, { text: string; color: string }> = {
        [TaskStatusValues.INIT]: {text: '已创建', color: 'default'},
        [TaskStatusValues.ASSIGNED]: {text: '已分配', color: 'default'},
        [TaskStatusValues.COLLECTING]: {text: '结果收集中', color: 'processing'},
        [TaskStatusValues.ONGOING]: {text: '运行中', color: 'processing'},
        [TaskStatusValues.SHUTDOWN]: {text: '已完成', color: 'success'},
        [TaskStatusValues.SHUTDOWN_ING]: {text: '正在结束', color: 'warning'},
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
            render: (_: unknown, record: TaskListItem) => (
                <Space.Compact size="small">
                    <Button
                        type="link"
                        icon={<EyeOutlined/>}
                        onClick={() => onViewDetail(record.id, record.taskId)}
                    >
                        详情
                    </Button>
                    <Button
                        type="link"
                        icon={<EditOutlined/>}
                        onClick={() => onEdit(record)}
                        disabled={record.status !== TaskStatusValues.INIT}
                        hidden={record.status !== TaskStatusValues.INIT}
                        title={record.status !== TaskStatusValues.INIT ? '只能在"已创建"状态编辑' : '编辑任务'}
                    >
                        编辑
                    </Button>

                    <Button
                        type="link"
                        icon={<CheckCircleOutlined/>}
                        onClick={() => onConfirm(record.id)}
                        disabled={record.status !== TaskStatusValues.ASSIGNED}
                        hidden={record.status !== TaskStatusValues.ASSIGNED}
                        title={record.status !== TaskStatusValues.INIT ? '只能在"已创建"状态确认' : '确认开始任务'}
                    >
                        确认
                    </Button>
                    <Button
                        type="link"
                        icon={<DeploymentUnitOutlined/>}
                        hidden={record.status !== TaskStatusValues.INIT}
                        onClick={() => onAssign(record)}
                        disabled={record.status !== TaskStatusValues.INIT}
                        title={record.status !== TaskStatusValues.INIT ? '只能在"已创建"状态分配' : '分配任务到集群节点'}
                    >
                        分配
                    </Button>
                    <Button
                        type="link"
                        icon={<StopOutlined/>}
                        onClick={() => onStop(record.id)}
                        hidden={record.status !== TaskStatusValues.ONGOING}
                        disabled={record.status !== TaskStatusValues.ONGOING}
                        title={record.status !== TaskStatusValues.ONGOING ? '只能在"运行中"状态停止' : '停止任务'}
                    >
                        停止
                    </Button>
                    <Popconfirm
                        title="确定要删除这个任务吗？"
                        onConfirm={() => onDelete(record.id)}
                        okText="确定"
                        cancelText="取消"
                    >
                        <Button
                            type="link"
                            danger
                            icon={<DeleteOutlined/>}
                        >
                            删除
                        </Button>
                    </Popconfirm>
                </Space.Compact>
            ),
        },
    ];

    return (
        <Table
            columns={columns}
            dataSource={tasks}
            rowKey="taskId"
            pagination={{
                pageSize: 10,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total) => `共 ${total} 条记录`,
            }}
        />
    );
};

export default TaskList;