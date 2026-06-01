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

import React from 'react';
import type {SpaceProps} from 'antd';
import {Button, Space} from 'antd';
import {
    CheckCircleOutlined,
    DeleteOutlined,
    DeploymentUnitOutlined,
    EditOutlined,
    EyeOutlined,
    StopOutlined
} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import {TaskStatusValues} from '../features/task';
import {isTaskRunning} from '../utils/taskUtils';

interface TaskActionButtonsProps extends SpaceProps {
    // view details
    onViewDetail?: () => void;
    // edit
    onEdit?: () => void;
    // confirm
    onConfirm?: () => void;
    // assign
    onAssign?: () => void;
    // stop
    onStop?: () => void;
    // delete
    onDelete?: () => void;

    // current status
    status?: string;

    // button size
    size?: SpaceProps['size'];
}

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
    const {t} = useTranslation();
    const TaskStatus = TaskStatusValues;

    // Runtime model states: STARTING/ONGOING/SHUTTING.
    const isRunning = status ? isTaskRunning(status) : false;
    const isStopping = status === TaskStatus.SHUTTING;

    // Button display rules:
    // INIT (created): edit, assign, delete
    // ASSIGNED (assigned): re-assign, confirm, delete
    // Running (STARTING/ONGOING): stop
    // SHUTTING: disabled stopping indicator
    // STOPPED/SHUTDOWN: delete
    const showEdit = status === TaskStatus.INIT;
    const showAssign = status === TaskStatus.INIT || status === TaskStatus.ASSIGNED;
    const showConfirm = status === TaskStatus.ASSIGNED;
    const showStop = isRunning;
    const showDelete = !isRunning;

    return (
        <Space size={size}>
            {onViewDetail && (
                <Button type="link" icon={<EyeOutlined/>} onClick={onViewDetail}>
                    {t('common.detail')}
                </Button>
            )}
            {onEdit && showEdit && (
                <Button type="link" icon={<EditOutlined/>} onClick={onEdit}>
                    {t('common.edit')}
                </Button>
            )}
            {onAssign && showAssign && (
                <Button type="link" icon={<DeploymentUnitOutlined/>} onClick={onAssign}>
                    {status === TaskStatus.ASSIGNED ? t('common.reassign') : t('common.assign')}
                </Button>
            )}
            {onConfirm && showConfirm && (
                <Button type="link" icon={<CheckCircleOutlined/>} onClick={onConfirm}>
                    {t('common.confirm')}
                </Button>
            )}
            {onStop && showStop && (
                <Button type="link" icon={<StopOutlined/>} disabled={isStopping} onClick={onStop}>
                    {isStopping ? t('task.status.SHUTTING') : t('common.stop')}
                </Button>
            )}
            {onDelete && showDelete && (
                <Button type="link" danger icon={<DeleteOutlined/>} onClick={onDelete}>
                    {t('common.delete')}
                </Button>
            )}
        </Space>
    );
};

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
                                                                    }) => {
    const {t} = useTranslation();
    return (
    <Space size={size}>
        {onEdit && (
            <Button type="link" icon={<EditOutlined/>} onClick={onEdit}>
                {t('common.edit')}
            </Button>
        )}
        {onConfirm && (
            <Button type="link" icon={<CheckCircleOutlined/>} onClick={onConfirm}>
                {t('common.confirm')}
            </Button>
        )}
        {onAssign && (
            <Button type="link" icon={<DeploymentUnitOutlined/>} onClick={onAssign}>
                {t('common.assign')}
            </Button>
        )}
        {onDelete && (
            <Button type="link" danger icon={<DeleteOutlined/>} onClick={onDelete}>
                {t('common.delete')}
            </Button>
        )}
    </Space>
    );
};
