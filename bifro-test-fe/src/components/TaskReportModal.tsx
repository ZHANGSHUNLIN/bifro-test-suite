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
import {Modal} from 'antd';
import {BarChartOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import TaskReportPanel from './TaskReportPanel';

interface TaskReportModalProps {
    open: boolean;
    taskId: string;
    taskName?: string;
    taskType?: string;
    onClose: () => void;
}

const TaskReportModal: React.FC<TaskReportModalProps> = ({open, taskId, taskName, taskType, onClose}) => {
    const {t} = useTranslation();
    return (
    <Modal
        open={open}
        onCancel={onClose}
        footer={null}
        width={960}
        title={
            <span style={{display: 'flex', alignItems: 'center', gap: 8}}>
                <BarChartOutlined style={{color: '#00b173'}}/>
                {t('task.detail.reportTitle')}{taskName ? ` · ${taskName}` : ''}
            </span>
        }
        destroyOnClose
        styles={{body: {padding: '12px 0 0', maxHeight: '75vh', overflowY: 'auto'}}}
    >
        <TaskReportPanel taskId={taskId} taskType={taskType}/>
    </Modal>
    );
};

export default TaskReportModal;
