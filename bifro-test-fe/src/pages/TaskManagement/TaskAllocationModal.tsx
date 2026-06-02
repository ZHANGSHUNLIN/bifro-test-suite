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

import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {Alert, Button, Input, InputNumber, message, Modal, Popconfirm, Space, Table, Tag, Typography} from 'antd';
import type {TableProps} from 'antd';
import clusterApi from '../../features/cluster';
import type {NodeListVO} from '../../features/cluster';
import type {NodeAllocation, NodeTaskAllocationRequest} from '../../features/task';
import {taskApi} from '../../features/task';
import {CheckOutlined, DeleteOutlined, PlusOutlined, SearchOutlined, SyncOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import {formatBytes} from './utils';

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
    const [allocationData, setAllocationData] = useState<NodeTaskAllocationRequest | null>(null);
    const [editingData, setEditingData] = useState<NodeAllocation[]>([]);
    const [allNodes, setAllNodes] = useState<NodeListVO[]>([]);
    const [schedulableNodeIds, setSchedulableNodeIds] = useState<Set<string>>(new Set());
    const [nodeById, setNodeById] = useState<Record<string, NodeListVO>>({});
    const [nodeSearch, setNodeSearch] = useState('');
    const [selectedAvailableNodeIds, setSelectedAvailableNodeIds] = useState<React.Key[]>([]);
    const [selectedAllocationNodeIds, setSelectedAllocationNodeIds] = useState<React.Key[]>([]);
    const [batchClientCount, setBatchClientCount] = useState<number | null>(null);
    const [assignError, setAssignError] = useState<string | null>(null);

    const errorMessage = useCallback((fallback: string, error: unknown): string => {
        if (error instanceof Error && error.message) {
            return `${fallback}: ${error.message}`;
        }
        return fallback;
    }, []);

    const loadSchedulableNodes = useCallback(async () => {
        const nodes = await clusterApi.getAllNodes();
        const nextNodeById = Object.fromEntries(nodes.map(node => [node.nodeId, node]));
        setAllNodes(nodes);
        setNodeById(nextNodeById);
        setSchedulableNodeIds(new Set(nodes.filter(node => node.schedulable).map(node => node.nodeId)));
        return nodes;
    }, []);

    const evenlyAllocate = useCallback((totalClientCount: number, nodeIds: string[]): NodeAllocation[] => {
        if (nodeIds.length === 0) {
            return [];
        }
        const baseCount = Math.floor(totalClientCount / nodeIds.length);
        const remainder = totalClientCount % nodeIds.length;
        return nodeIds.map((nodeId, index) => ({
            nodeId,
            allocatedClientCount: baseCount + (index < remainder ? 1 : 0),
        }));
    }, []);

    const loadInitialAllocation = useCallback(async () => {
        setCalculating(true);
        try {
            const [taskConfig, nodes] = await Promise.all([
                taskApi.getTaskConfig(taskId),
                loadSchedulableNodes(),
            ]);
            const totalClientCount = taskConfig.totalClientCount || 0;
            const schedulableNodeIds = nodes
                .filter(node => node.schedulable)
                .map(node => node.nodeId);
            const nodeAllocationList = evenlyAllocate(totalClientCount, schedulableNodeIds);
            setAllocationData({totalClientCount, nodeAllocationList});
            setEditingData(nodeAllocationList);
            setSelectedAvailableNodeIds([]);
            setSelectedAllocationNodeIds([]);
            setAssignError(null);
        } catch (error) {
            const detail = errorMessage(t('task.msg.loadAssignFailed'), error);
            setAssignError(detail);
            message.error(detail);
            console.error('Load initial allocation failed:', error);
        } finally {
            setCalculating(false);
        }
    }, [errorMessage, evenlyAllocate, loadSchedulableNodes, t, taskId]);

    // Pre-fill existing allocation (used in ASSIGNED state)
    const loadExistingAllocation = useCallback(async () => {
        setCalculating(true);
        try {
            const [resp] = await Promise.all([
                taskApi.getTaskSubTasks(taskId),
                loadSchedulableNodes(),
            ]);
            const subTaskDetails = resp.subTaskDetails;
            if (subTaskDetails && Object.keys(subTaskDetails).length > 0) {
                const nodeAllocationList: NodeAllocation[] = Object.values(subTaskDetails).map(detail => ({
                    nodeId: detail.nodeId,
                    allocatedClientCount: detail.totalClientCount,
                }));
                const totalClientCount = nodeAllocationList.reduce((sum, n) => sum + n.allocatedClientCount, 0);
                setAllocationData({totalClientCount, nodeAllocationList});
                setEditingData(nodeAllocationList);
                setSelectedAvailableNodeIds([]);
                setSelectedAllocationNodeIds([]);
            } else {
                await loadInitialAllocation();
            }
        } catch (error) {
            message.error(t('task.msg.loadAssignFailed'));
            console.error('Load existing allocation failed:', error);
            await loadInitialAllocation();
        } finally {
            setCalculating(false);
        }
    }, [loadInitialAllocation, loadSchedulableNodes, t, taskId]);

    // On modal open: pre-fill allocation in ASSIGNED state, otherwise recalculate
    useEffect(() => {
        if (visible && taskId) {
            if (taskStatus === 'ASSIGNED') {
                loadExistingAllocation();
            } else {
                loadInitialAllocation();
            }
        }
    }, [loadExistingAllocation, loadInitialAllocation, taskId, taskStatus, visible]);

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
            setSelectedAllocationNodeIds(prev => prev.filter(key => key !== nodeId));
        }
    };

    const addNodesToAllocation = useCallback((nodeIds: React.Key[]) => {
        if (nodeIds.length === 0) {
            return;
        }
        setEditingData(prev => {
            const assignedNodeIds = new Set(prev.map(item => item.nodeId));
            const nextItems = nodeIds
                .map(String)
                .filter(nodeId => schedulableNodeIds.has(nodeId) && !assignedNodeIds.has(nodeId))
                .map(nodeId => ({nodeId, allocatedClientCount: 0}));
            return [...prev, ...nextItems];
        });
        setSelectedAvailableNodeIds([]);
    }, [schedulableNodeIds]);

    const handleBatchSetClients = () => {
        if (batchClientCount === null || selectedAllocationNodeIds.length === 0) {
            return;
        }
        const selectedNodeIds = new Set(selectedAllocationNodeIds.map(String));
        setEditingData(prev => prev.map(item => selectedNodeIds.has(item.nodeId)
            ? {...item, allocatedClientCount: batchClientCount}
            : item
        ));
    };

    const handleBatchRemoveNodes = () => {
        const selectedNodeIds = new Set(selectedAllocationNodeIds.map(String));
        setEditingData(prev => prev.filter(item => !selectedNodeIds.has(item.nodeId)));
        setSelectedAllocationNodeIds([]);
    };

    const handleRebalanceCurrentNodes = () => {
        if (!allocationData || editingData.length === 0) {
            message.warning(t('task.allocationModal.rebalanceEmpty'));
            return;
        }
        const baseCount = Math.floor(allocationData.totalClientCount / editingData.length);
        const remainder = allocationData.totalClientCount % editingData.length;
        setEditingData(prev => prev.map((item, index) => ({
            ...item,
            allocatedClientCount: baseCount + (index < remainder ? 1 : 0),
        })));
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
        const invalidNodeIds = editingData
            .map(item => item.nodeId)
            .filter(nodeId => !schedulableNodeIds.has(nodeId));
        if (invalidNodeIds.length > 0) {
            message.error(t('task.allocationModal.unschedulableSelected', {nodes: invalidNodeIds.join(', ')}));
            return;
        }

        setLoading(true);
        try {
            const requestData: NodeTaskAllocationRequest = {
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
        setAllNodes([]);
        setSchedulableNodeIds(new Set());
        setNodeById({});
        setNodeSearch('');
        setSelectedAvailableNodeIds([]);
        setSelectedAllocationNodeIds([]);
        setBatchClientCount(null);
        setAssignError(null);
        onCancel();
    };

    const assignedNodeIds = useMemo(
        () => new Set(editingData.map(item => item.nodeId)),
        [editingData]
    );

    const availableNodes = useMemo(
        () => allNodes.filter(node => node.schedulable && !assignedNodeIds.has(node.nodeId)),
        [allNodes, assignedNodeIds]
    );

    const filteredAvailableNodes = useMemo(() => {
        const searchValue = nodeSearch.trim().toLowerCase();
        if (!searchValue) {
            return availableNodes;
        }
        return availableNodes.filter(node =>
            node.nodeId.toLowerCase().includes(searchValue) ||
            (node.nodeName || '').toLowerCase().includes(searchValue) ||
            (node.host || '').toLowerCase().includes(searchValue)
        );
    }, [availableNodes, nodeSearch]);

    useEffect(() => {
        const availableNodeIds = new Set(availableNodes.map(node => node.nodeId));
        setSelectedAvailableNodeIds(prev => prev.filter(nodeId => availableNodeIds.has(String(nodeId))));
    }, [availableNodes]);

    useEffect(() => {
        setSelectedAllocationNodeIds(prev => prev.filter(nodeId => assignedNodeIds.has(String(nodeId))));
    }, [assignedNodeIds]);

    const renderNodeIdentity = (nodeId: string) => {
        const node = nodeById[nodeId];
        return (
            <div className="task-allocation-node-cell">
                <div className="task-allocation-node-line">
                    <Text strong>{node?.nodeName || nodeId}</Text>
                    {!schedulableNodeIds.has(nodeId) && (
                        <Tag color="warning">{t('cluster.schedulable.no')}</Tag>
                    )}
                </div>
                <Text type="secondary" code>{nodeId}</Text>
            </div>
        );
    };

    const renderNodeCapacity = (node?: NodeListVO) => (
        <Space size={[4, 4]} wrap>
            <Tag>{t('task.allocationModal.capacityCpu', {n: node?.cpu?.processors ?? 0})}</Tag>
            <Tag>{t('task.allocationModal.capacityMem', {v: formatBytes(node?.memory?.total || 0)})}</Tag>
        </Space>
    );

    const availableNodeColumns: TableProps<NodeListVO>['columns'] = [
        {
            title: t('task.allocationModal.columns.nodeId'),
            dataIndex: 'nodeId',
            key: 'nodeId',
            render: (nodeId: string) => renderNodeIdentity(nodeId),
        },
        {
            title: t('task.allocationModal.columns.capacity'),
            key: 'capacity',
            width: 210,
            render: (_: unknown, record: NodeListVO) => renderNodeCapacity(record),
        },
    ];

    const availableRowSelection: TableProps<NodeListVO>['rowSelection'] = {
        selectedRowKeys: selectedAvailableNodeIds,
        onChange: setSelectedAvailableNodeIds,
        preserveSelectedRowKeys: false,
    };

    const allocationRowSelection: TableProps<NodeAllocation>['rowSelection'] = {
        selectedRowKeys: selectedAllocationNodeIds,
        onChange: setSelectedAllocationNodeIds,
    };

    const columns = [
        {
            title: t('task.allocationModal.columns.nodeId'),
            dataIndex: 'nodeId',
            key: 'nodeId',
            width: '38%',
            render: (nodeId: string) => renderNodeIdentity(nodeId),
        },
        {
            title: t('task.allocationModal.columns.capacity'),
            key: 'capacity',
            width: '25%',
            render: (_: unknown, record: NodeAllocation) => renderNodeCapacity(nodeById[record.nodeId]),
        },
        {
            title: t('task.allocationModal.columns.assignedClients'),
            dataIndex: 'allocatedClientCount',
            key: 'allocatedClientCount',
            width: '25%',
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
            width: '12%',
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
            width={960}
            footer={[
                <Button key="cancel" onClick={handleClose}>
                    {t('common.cancel')}
                </Button>,
                <Button
                    key="recalculate"
                    icon={<SyncOutlined/>}
                    onClick={handleRebalanceCurrentNodes}
                    disabled={!allocationData || editingData.length === 0}
                >
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
                            <Text type="secondary" style={{marginLeft: 12}}>
                                {t('task.allocationModal.selectedNodes', {count: editingData.length})}
                            </Text>
                        </div>

                        <div className="task-allocation-section">
                            <div className="task-allocation-toolbar">
                                <Text strong>{t('task.allocationModal.nodePool')}</Text>
                                <Space wrap>
                                    <Input
                                        allowClear
                                        prefix={<SearchOutlined/>}
                                        value={nodeSearch}
                                        onChange={event => setNodeSearch(event.target.value)}
                                        placeholder={t('task.allocationModal.searchNode')}
                                        style={{width: 240}}
                                    />
                                    <Button
                                        icon={<PlusOutlined/>}
                                        onClick={() => addNodesToAllocation(selectedAvailableNodeIds)}
                                        disabled={selectedAvailableNodeIds.length === 0}
                                    >
                                        {t('task.allocationModal.addSelected')}
                                    </Button>
                                    <Button
                                        icon={<PlusOutlined/>}
                                        onClick={() => addNodesToAllocation(filteredAvailableNodes.map(node => node.nodeId))}
                                        disabled={filteredAvailableNodes.length === 0}
                                    >
                                        {t('task.allocationModal.addFiltered')}
                                    </Button>
                                </Space>
                            </div>
                            <Table<NodeListVO>
                                columns={availableNodeColumns}
                                dataSource={filteredAvailableNodes}
                                rowKey="nodeId"
                                rowSelection={availableRowSelection}
                                pagination={{pageSize: 6, showSizeChanger: false, size: 'small'}}
                                size="small"
                                locale={{emptyText: t('task.allocationModal.noAvailableNodes')}}
                            />
                        </div>

                        <div className="task-allocation-section">
                            <div className="task-allocation-toolbar">
                                <Text strong>{t('task.allocationModal.allocatedNodes')}</Text>
                                <Space wrap>
                                    <Text type="secondary">
                                        {t('task.allocationModal.selectedRows', {count: selectedAllocationNodeIds.length})}
                                    </Text>
                                    <InputNumber
                                        min={0}
                                        value={batchClientCount}
                                        onChange={setBatchClientCount}
                                        placeholder={t('task.allocationModal.batchClientPlaceholder')}
                                        style={{width: 170}}
                                    />
                                    <Button
                                        icon={<CheckOutlined/>}
                                        onClick={handleBatchSetClients}
                                        disabled={batchClientCount === null || selectedAllocationNodeIds.length === 0}
                                    >
                                        {t('task.allocationModal.batchSetClients')}
                                    </Button>
                                    <Popconfirm
                                        title={t('common.deleteConfirm')}
                                        onConfirm={handleBatchRemoveNodes}
                                        okText={t('common.confirm')}
                                        cancelText={t('common.cancel')}
                                        disabled={selectedAllocationNodeIds.length === 0}
                                    >
                                        <Button
                                            danger
                                            icon={<DeleteOutlined/>}
                                            disabled={selectedAllocationNodeIds.length === 0}
                                        >
                                            {t('task.allocationModal.removeSelected')}
                                        </Button>
                                    </Popconfirm>
                                </Space>
                            </div>

                            <Table
                                columns={columns}
                                dataSource={editingData}
                                rowKey="nodeId"
                                rowSelection={allocationRowSelection}
                                pagination={editingData.length > 10 ? {pageSize: 10, showSizeChanger: false, size: 'small'} : false}
                                size="small"
                            />
                        </div>

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
