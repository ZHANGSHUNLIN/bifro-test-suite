import React, { useState, useEffect } from 'react';
import { Modal, InputNumber, Table, Typography, Space, Button, message, Select, Popconfirm } from 'antd';
import type { NodeTaskAllocationVO, NodeAllocation } from '../../types/task';
import taskApi from '../../services/taskApi';
import { DeleteOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface TaskAllocationModalProps {
  visible: boolean;
  taskId: string;
  taskName?: string;
  onCancel: () => void;
  onSuccess: () => void;
}

const TaskAllocationModal: React.FC<TaskAllocationModalProps> = ({
  visible,
  taskId,
  taskName,
  onCancel,
  onSuccess,
}) => {
  const [loading, setLoading] = useState(false);
  const [calculating, setCalculating] = useState(false);
  const [allocationData, setAllocationData] = useState<NodeTaskAllocationVO | null>(null);
  const [editingData, setEditingData] = useState<NodeAllocation[]>([]);
  const [availableNodes, setAvailableNodes] = useState<string[]>([]);
  const [selectedNodeToAdd, setSelectedNodeToAdd] = useState<string | undefined>(undefined);

  // 计算分配
  const calculateAllocation = async () => {
    setCalculating(true);
    try {
      const data = await taskApi.calculateNodeTaskAllocation(taskId);
      setAllocationData(data);
      setEditingData(data.nodeAllocationList || []);
      // 初始化时所有节点都在编辑数据中，所以可用节点为空
      setAvailableNodes([]);
    } catch (error) {
      message.error('计算分配失败');
      console.error('Calculate allocation failed:', error);
    } finally {
      setCalculating(false);
    }
  };

  // 弹窗打开时自动计算
  useEffect(() => {
    if (visible && taskId) {
      calculateAllocation();
    }
  }, [visible, taskId]);

  // 处理客户端数量变化
  const handleCountChange = (nodeId: string, value: number | null) => {
    const newValue = value || 0;
    setEditingData(prev =>
      prev.map(item =>
        item.nodeId === nodeId ? { ...item, allocatedClientCount: newValue } : item
      )
    );
  };

  // 删除节点
  const handleRemoveNode = (nodeId: string) => {
    const nodeToRemove = editingData.find(item => item.nodeId === nodeId);
    if (nodeToRemove) {
      setEditingData(prev => prev.filter(item => item.nodeId !== nodeId));
      setAvailableNodes(prev => [...prev, nodeId]);
    }
  };

  // 添加节点
  const handleAddNode = () => {
    if (selectedNodeToAdd && !editingData.some(item => item.nodeId === selectedNodeToAdd)) {
      setEditingData(prev => [...prev, { nodeId: selectedNodeToAdd, allocatedClientCount: 0 }]);
      setAvailableNodes(prev => prev.filter(nodeId => nodeId !== selectedNodeToAdd));
      setSelectedNodeToAdd(undefined);
    }
  };

  // 提交分配
  const handleSubmit = async () => {
    if (!allocationData) return;

    const totalAllocated = editingData.reduce((sum, item) => sum + item.allocatedClientCount, 0);
    if (totalAllocated !== allocationData.totalClientCount) {
      message.error(`客户端数量总和必须等于 ${allocationData.totalClientCount}，当前为 ${totalAllocated}`);
      return;
    }

    setLoading(true);
    try {
      const requestData: NodeTaskAllocationVO = {
        totalClientCount: allocationData.totalClientCount,
        nodeAllocationList: editingData,
      };
      await taskApi.assignTask(taskId, requestData);
      message.success('任务分配成功');
      onSuccess();
      handleClose();
    } catch (error) {
      message.error('任务分配失败');
      console.error('Assign task failed:', error);
    } finally {
      setLoading(false);
    }
  };

  // 关闭弹窗
  const handleClose = () => {
    setAllocationData(null);
    setEditingData([]);
    setAvailableNodes([]);
    setSelectedNodeToAdd(undefined);
    onCancel();
  };

  const columns = [
    {
      title: '节点ID',
      dataIndex: 'nodeId',
      key: 'nodeId',
      width: '35%',
    },
    {
      title: '分配客户端数',
      dataIndex: 'allocatedClientCount',
      key: 'allocatedClientCount',
      width: '45%',
      render: (_: unknown, record: NodeAllocation) => (
        <InputNumber
          min={0}
          value={record.allocatedClientCount}
          onChange={(value) => handleCountChange(record.nodeId, value)}
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: '20%',
      render: (_: unknown, record: NodeAllocation) => (
        <Popconfirm
          title="确定要移除该节点吗？"
          onConfirm={() => handleRemoveNode(record.nodeId)}
          okText="确定"
          cancelText="取消"
        >
          <Button type="link" danger icon={<DeleteOutlined />} size="small">
            移除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  const totalAllocated = editingData.reduce((sum, item) => sum + item.allocatedClientCount, 0);

  return (
    <Modal
      title={`分配任务 - ${taskName || taskId}`}
      open={visible}
      onCancel={handleClose}
      width={600}
      footer={[
        <Button key="cancel" onClick={handleClose}>
          取消
        </Button>,
        <Button key="recalculate" onClick={calculateAllocation} loading={calculating}>
          重新计算
        </Button>,
        <Button
          key="submit"
          type="primary"
          onClick={handleSubmit}
          loading={loading}
          disabled={!allocationData}
        >
          确认分配
        </Button>,
      ]}
    >
      {calculating && (
        <div style={{ textAlign: 'center', padding: '20px 0' }}>
          正在计算分配方案...
        </div>
      )}

      {allocationData && !calculating && (
        <div>
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <div>
              <Text>总客户端数: </Text>
              <Text strong>{allocationData.totalClientCount}</Text>
            </div>

            {/* 添加节点区域 */}
            {availableNodes.length > 0 && (
              <Space direction="horizontal" size="small" style={{ width: '100%' }}>
                <Text>添加节点:</Text>
                <Select
                  style={{ flex: 1 }}
                  placeholder="选择要添加的节点"
                  value={selectedNodeToAdd}
                  onChange={setSelectedNodeToAdd}
                  options={availableNodes.map(nodeId => ({ label: nodeId, value: nodeId }))}
                />
                <Button
                  type="primary"
                  onClick={handleAddNode}
                  disabled={!selectedNodeToAdd}
                >
                  添加
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

            <div style={{ textAlign: 'right' }}>
              <Text>
                已分配客户端数:{' '}
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
                警告: 分配的客户端总数必须等于总客户端数
              </Text>
            )}

            {editingData.length === 0 && (
              <Text type="warning">
                提示: 当前没有选择任何节点，请添加节点后进行分配
              </Text>
            )}
          </Space>
        </div>
      )}
    </Modal>
  );
};

export default TaskAllocationModal;