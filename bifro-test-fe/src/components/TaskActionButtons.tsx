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

    // 按钮显示规则
    const showEdit = status === TaskStatus.INIT;
    const showConfirm = status === TaskStatus.INIT || status === TaskStatus.ASSIGNED;
    const showAssign = status === TaskStatus.INIT;
    const showStop = status === TaskStatus.ONGOING;
    const showDelete = status !== TaskStatus.ONGOING;

    return (
        <Space size={size}>
            {onViewDetail && (
                <Button type="link" icon={<EyeOutlined/>} onClick={onViewDetail}>
                    详情
                </Button>
            )}
            {onEdit && (
                <Button
                    type="link"
                    icon={<EditOutlined/>}
                    onClick={onEdit}
                    disabled={!showEdit}
                    hidden={!showEdit}
                    title={!showEdit ? '只能在"已创建"状态编辑' : undefined}
                >
                    编辑
                </Button>
            )}
            {onConfirm && (
                <Button
                    type="link"
                    icon={<CheckCircleOutlined/>}
                    onClick={onConfirm}
                    disabled={!showConfirm}
                    hidden={!showConfirm}
                    title={!showConfirm ? '只能在"已创建"或"已分配"状态确认' : undefined}
                >
                    确认
                </Button>
            )}
            {onAssign && (
                <Button
                    type="link"
                    icon={<DeploymentUnitOutlined/>}
                    onClick={onAssign}
                    disabled={!showAssign}
                    hidden={!showAssign}
                    title={!showAssign ? '只能在"已创建"状态分配' : undefined}
                >
                    分配
                </Button>
            )}
            {onStop && (
                <Button
                    type="link"
                    icon={<StopOutlined/>}
                    onClick={onStop}
                    disabled={!showStop}
                    hidden={!showStop}
                    title={!showStop ? '只能在"运行中"状态停止' : undefined}
                >
                    停止
                </Button>
            )}
            {onDelete && (
                <Button
                    type="link"
                    danger
                    icon={<DeleteOutlined/>}
                    onClick={onDelete}
                    hidden={!showDelete}
                    title={!showDelete ? '运行中的任务不能删除' : undefined}
                >
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
