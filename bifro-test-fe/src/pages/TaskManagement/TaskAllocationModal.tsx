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

import React, {useEffect, useState} from 'react';
import {Alert, Button, InputNumber, message, Modal, Popconfirm, Select, Space, Table, Typography} from 'antd';
import type {NodeAllocation, NodeTaskAllocationVO} from '../../features/task';
import {taskApi} from '../../features/task';
import {DeleteOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';

const {Text} = Typography;

interface TaskAllocationModalProps {
    visible: boolean;
    taskId: string;
    taskName?: string;
    taskStatus?: string;
    onCancel: () => void;
    onSuccess: () => void;
}

const TaskAllocationModal: React.FC<TaskAllocationModalProps> = ({
                                                                     visible,
                                                                     taskId,
                                                                     taskName,
                                                                     taskStatus,
                                                                     onCancel,
                                                                     onSuccess,
                                                                 }) => {
    const {t} = useTranslation();
    const [loading, setLoading] = useState(false);
    const [calculating, setCalculating] = useState(false);
    const [allocationData, setAllocationData] = useState<NodeTaskAllocationVO | null>(null);
    const [editingData, setEditingData] = useState<NodeAllocation[]>([]);
    const [availableNodes, setAvailableNodes] = useState<string[]>([]);
    const [selectedNodeToAdd, setSelectedNodeToAdd] = useState<string | undefined>(undefined);
    const [assignError, setAssignError] = useState<string | null>(null);

    const errorMessage = (fallback: string, error: unknown): string => {
        if (error instanceof Error && error.message) {
            return `${fallback}: ${error.message}`;
        }
        return fallback;
    };

    // Calculate allocation (always triggers recalculation)
    const calculateAllocation = async () => {
        setCalculating(true);
        try {
            const data = await taskApi.calculateNodeTaskAllocation(taskId);
            setAllocationData(data);
            setEditingData(data.nodeAllocationList || []);
            setAvailableNodes([]);
            setAssignError(null);
        } catch (error) {
            const detail = errorMessage(t('task.msg.calcAssignFailed'), error);
            setAssignError(detail);
            message.error(detail);
            console.error('Calculate allocation failed:', error);
        } finally {
            setCalculating(false);
        }
    };

    // Pre-fill existing allocation (used in ASSIGNED state)
    const loadExistingAllocation = async () => {
        setCalculating(true);
        try {
            const resp = await taskApi.getTaskSubTasks(taskId);
            const subTaskDetails = resp.subTaskDetails;
            if (subTaskDetails && Object.keys(subTaskDetails).length > 0) {
                const nodeAllocationList: NodeAllocation[] = Object.values(subTaskDetails).map(detail => ({
                    nodeId: detail.nodeId,
                    allocatedClientCount: detail.totalClientCount,
                }));
                const totalClientCount = nodeAllocationList.reduce((sum, n) => sum + n.allocatedClientCount, 0);
                setAllocationData({totalClientCount, nodeAllocationList});
                setEditingData(nodeAllocationList);
                setAvailableNodes([]);
            } else {
                // Subtask data missing, fallback to recalculation
                await calculateAllocation();
            }
        } catch (error) {
            message.error(t('task.msg.loadAssignFailed'));
            console.error('Load existing allocation failed:', error);
            await calculateAllocation();
        } finally {
            setCalculating(false);
        }
    };

    // On modal open: pre-fill allocation in ASSIGNED state, otherwise recalculate
    useEffect(() => {
        if (visible && taskId) {
            if (taskStatus === 'ASSIGNED') {
                loadExistingAllocation();
            } else {
                calculateAllocation();
            }
        }
    }, [visible, taskId]);

    // Handle client count change
    const handleCountChange = (nodeId: string, value: number | null) => {
        const newValue = value || 0;
        setEditingData(prev =>
            prev.map(item =>
                item.nodeId === nodeId ? {...item, allocatedClientCount: newValue} : item
            )
        );
    };

    // remove node
    const handleRemoveNode = (nodeId: string) => {
        const nodeToRemove = editingData.find(item => item.nodeId === nodeId);
        if (nodeToRemove) {
            setEditingData(prev => prev.filter(item => item.nodeId !== nodeId));
            setAvailableNodes(prev => [...prev, nodeId]);
        }
    };

    // Add node
    const handleAddNode = () => {
        if (selectedNodeToAdd && !editingData.some(item => item.nodeId === selectedNodeToAdd)) {
            setEditingData(prev => [...prev, {nodeId: selectedNodeToAdd, allocatedClientCount: 0}]);
            setAvailableNodes(prev => prev.filter(nodeId => nodeId !== selectedNodeToAdd));
            setSelectedNodeToAdd(undefined);
        }
    };

    // Submit allocation
    const handleSubmit = async () => {
        if (!allocationData) return;
        setAssignError(null);

        const totalAllocated = editingData.reduce((sum, item) => sum + item.allocatedClientCount, 0);
        if (totalAllocated !== allocationData.totalClientCount) {
            message.error(t('task.allocationModal.totalMismatch', {total: allocationData.totalClientCount, current: totalAllocated}));
            return;
        }

        setLoading(true);
        try {
            const requestData: NodeTaskAllocationVO = {
                totalClientCount: allocationData.totalClientCount,
                nodeAllocationList: editingData,
            };
            await taskApi.assignTask(taskId, requestData);
            message.success(t('task.msg.assignSuccess'));
            onSuccess();
            handleClose();
        } catch (error) {
            const detail = errorMessage(t('task.msg.assignFailed'), error);
            setAssignError(detail);
            message.error(detail);
            console.error('Assign task failed:', error);
        } finally {
            setLoading(false);
        }
    };

    // Close modal
    const handleClose = () => {
        setAllocationData(null);
        setEditingData([]);
        setAvailableNodes([]);
        setSelectedNodeToAdd(undefined);
        setAssignError(null);
        onCancel();
    };

    const columns = [
        {
            title: t('task.allocationModal.columns.nodeId'),
            dataIndex: 'nodeId',
            key: 'nodeId',
            width: '35%',
        },
        {
            title: t('task.allocationModal.columns.assignedClients'),
            dataIndex: 'allocatedClientCount',
            key: 'allocatedClientCount',
            width: '45%',
            render: (_: unknown, record: NodeAllocation) => (
                <InputNumber
                    min={0}
                    value={record.allocatedClientCount}
                    onChange={(value) => handleCountChange(record.nodeId, value)}
                    style={{width: '100%'}}
                />
            ),
        },
        {
            title: t('task.allocationModal.columns.action'),
            key: 'action',
            width: '20%',
            render: (_: unknown, record: NodeAllocation) => (
                <Popconfirm
                    title={t('common.deleteConfirm')}
                    onConfirm={() => handleRemoveNode(record.nodeId)}
                    okText={t('common.confirm')}
                    cancelText={t('common.cancel')}
                >
                    <Button type="link" danger icon={<DeleteOutlined/>} size="small">
                        {t('common.delete')}
                    </Button>
                </Popconfirm>
            ),
        },
    ];

    const totalAllocated = editingData.reduce((sum, item) => sum + item.allocatedClientCount, 0);

    return (
        <Modal
            title={`${taskStatus === 'ASSIGNED' ? t('common.reassign') : t('common.assign')}${t('task.title')} - ${taskName || taskId}`}
            open={visible}
            onCancel={handleClose}
            width={600}
            footer={[
                <Button key="cancel" onClick={handleClose}>
                    {t('common.cancel')}
                </Button>,
                <Button key="recalculate" onClick={calculateAllocation} loading={calculating}>
                    {t('common.reassign')}
                </Button>,
                <Button
                    key="submit"
                    type="primary"
                    onClick={handleSubmit}
                    loading={loading}
                    disabled={!allocationData}
                >
                    {t('common.assign')}
                </Button>,
            ]}
        >
            {assignError && (
                <Alert
                    type="error"
                    showIcon
                    message={assignError}
                    style={{marginBottom: 16}}
                />
            )}

            {calculating && (
                <div style={{textAlign: 'center', padding: '20px 0'}}>
                    {t('common.loading')}
                </div>
            )}

            {allocationData && !calculating && (
                <div>
                    <Space direction="vertical" size="middle" style={{width: '100%'}}>
                        <div>
                            <Text>{t('task.totalClients')}: </Text>
                            <Text strong>{allocationData.totalClientCount}</Text>
                        </div>

                        {/* Add node area */}
                        {availableNodes.length > 0 && (
                            <Space direction="horizontal" size="small" style={{width: '100%'}}>
                                <Text>{t('common.assign')}:</Text>
                                <Select
                                    style={{flex: 1}}
                                    placeholder={t('common.assign')}
                                    value={selectedNodeToAdd}
                                    onChange={setSelectedNodeToAdd}
                                    options={availableNodes.map(nodeId => ({label: nodeId, value: nodeId}))}
                                />
                                <Button
                                    type="primary"
                                    onClick={handleAddNode}
                                    disabled={!selectedNodeToAdd}
                                >
                                    {t('common.assign')}
                                </Button>
                            </Space>
                        )}

                        <Table
                            columns={columns}
                            dataSource={editingData}
                            rowKey="nodeId"
                            pagination={false}
                            size="small"
                        />

                        <div style={{textAlign: 'right'}}>
                            <Text>
                                {t('task.allocationModal.columns.assignedClients')}:{' '}
                                <Text
                                    type={totalAllocated === allocationData.totalClientCount ? 'success' : 'danger'}
                                    strong
                                >
                                    {totalAllocated}
                                </Text>
                                {' / '}
                                {allocationData.totalClientCount}
                            </Text>
                        </div>

                        {totalAllocated !== allocationData.totalClientCount && (
                            <Text type="danger">
                                {t('task.allocationModal.totalMismatch', {total: allocationData.totalClientCount, current: totalAllocated})}
                            </Text>
                        )}

                        {editingData.length === 0 && (
                            <Text type="warning">
                                {t('common.noData')}
                            </Text>
                        )}
                    </Space>
                </div>
            )}
        </Modal>
    );
};

export default TaskAllocationModal;
