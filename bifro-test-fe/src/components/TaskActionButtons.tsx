import React from 'react';
import {Button, Space} from 'antd';
import type {SpaceProps} from 'antd';
import {
    EyeOutlined,
    EditOutlined,
    DeleteOutlined,
    CheckCircleOutlined,
    DeploymentUnitOutlined,
    StopOutlined
} from '@ant-design/icons';
import {TaskStatusValues} from '../types/task';

interface TaskActionButtonsProps extends SpaceProps {
    // 查看详情
    onViewDetail?: () => void;
    // 编辑
    onEdit?: () => void;
    // 确认
    onConfirm?: () => void;
    // 分配
    onAssign?: () => void;
    // 停止
    onStop?: () => void;
    // 删除
    onDelete?: () => void;

    // 当前状态
    status?: string;

    // 按钮大小
    size?: SpaceProps['size'];
}

/**
 * 任务操作按钮组
 * 统一管理操作按钮的显示逻辑
 */
export const TaskActionButtons: React.FC<TaskActionButtonsProps> = ({
    onViewDetail,
    onEdit,
    onConfirm,
    onAssign,
    onStop,
    onDelete,
    status,
    size = 'small'
}) => {
    const TaskStatus = TaskStatusValues;

    // 运行中状态集合
    const isRunning = status === TaskStatus.START
        || status === TaskStatus.CONNECTING
        || status === TaskStatus.INIT_PUB_CLIENT
        || status === TaskStatus.INIT_SUB_CLIENT
        || status === TaskStatus.ONGOING;

    // 按钮显示规则：
    // INIT(已创建): 编辑、分配、删除
    // ASSIGNED(已分配): 重新分配、确认、删除
    // 运行中(START/CONNECTING/INIT_PUB_CLIENT/INIT_SUB_CLIENT/ONGOING): 停止
    // STOPPED/SHUTDOWN: 删除
    const showEdit = status === TaskStatus.INIT;
    const showAssign = status === TaskStatus.INIT || status === TaskStatus.ASSIGNED;
    const showConfirm = status === TaskStatus.ASSIGNED;
    const showStop = isRunning;
    const showDelete = !isRunning;

    return (
        <Space size={size}>
            {onViewDetail && (
                <Button type="link" icon={<EyeOutlined/>} onClick={onViewDetail}>
                    详情
                </Button>
            )}
            {onEdit && showEdit && (
                <Button type="link" icon={<EditOutlined/>} onClick={onEdit}>
                    编辑
                </Button>
            )}
            {onAssign && showAssign && (
                <Button type="link" icon={<DeploymentUnitOutlined/>} onClick={onAssign}>
                    {status === TaskStatus.ASSIGNED ? '重新分配' : '分配'}
                </Button>
            )}
            {onConfirm && showConfirm && (
                <Button type="link" icon={<CheckCircleOutlined/>} onClick={onConfirm}>
                    确认
                </Button>
            )}
            {onStop && showStop && (
                <Button type="link" icon={<StopOutlined/>} onClick={onStop}>
                    停止
                </Button>
            )}
            {onDelete && showDelete && (
                <Button type="link" danger icon={<DeleteOutlined/>} onClick={onDelete}>
                    删除
                </Button>
            )}
        </Space>
    );
};

/**
 * 简化版操作按钮（仅部分按钮）
 */
interface SimpleTaskActionsProps extends SpaceProps {
    onEdit?: () => void;
    onDelete?: () => void;
    onConfirm?: () => void;
    onAssign?: () => void;
    size?: SpaceProps['size'];
}

export const SimpleTaskActions: React.FC<SimpleTaskActionsProps> = ({
    onEdit,
    onDelete,
    onConfirm,
    onAssign,
    size = 'small'
}) => (
    <Space size={size}>
        {onEdit && (
            <Button type="link" icon={<EditOutlined/>} onClick={onEdit}>
                编辑
            </Button>
        )}
        {onConfirm && (
            <Button type="link" icon={<CheckCircleOutlined/>} onClick={onConfirm}>
                确认
            </Button>
        )}
        {onAssign && (
            <Button type="link" icon={<DeploymentUnitOutlined/>} onClick={onAssign}>
                分配
            </Button>
        )}
        {onDelete && (
            <Button type="link" danger icon={<DeleteOutlined/>} onClick={onDelete}>
                删除
            </Button>
        )}
    </Space>
);
