import React, {useState, useEffect} from 'react';
import {
    Card,
    Table,
    Button,
    Space,
    Modal,
    Tag,
    Typography,
    Spin,
    Descriptions,
    Row,
    Col,
    Statistic,
    Progress,
    message,
    Popconfirm
} from 'antd';
import {
    EyeOutlined,
    ReloadOutlined,
    CheckCircleOutlined,
    CloseCircleOutlined,
    WarningOutlined,
    PlayCircleOutlined,
    PauseCircleOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import clusterApi from '../services/clusterApi';
import type {
    NodeListItem,
    ClusterStatistics,
    NodeStatus,
    NodeDetailResponse
} from '../types/cluster';
import {TaskStatusText, TaskStatusValues} from '../types/task';

const {Title} = Typography;

const ClusterManagement: React.FC = () => {
    const [nodes, setNodes] = useState<NodeListItem[]>([]);
    const [clusterStats, setClusterStats] = useState<ClusterStatistics | null>(null);
    const [loading, setLoading] = useState(false);
    const [detailModalVisible, setDetailModalVisible] = useState(false);
    const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
    const [nodeDetail, setNodeDetail] = useState<NodeDetailResponse | null>(null);

    // 加载集群数据
    const loadClusterData = async () => {
        setLoading(true);
        try {
            // 获取节点信息
            const nodesData = await clusterApi.getAllNodes();
            const nodeListItems: NodeListItem[] = Object.entries(nodesData).map(([nodeId, nodeData]) => {
                const clusterNodeInfo = nodeData.clusterNodeInfo || nodeData; // 兼容两种数据结构
                const lastHeartbeat = dayjs(nodeData.nextPing).format('YYYY-MM-DD HH:mm:ss');
                const isOnline = nodeData.alive;
                return {
                    nodeId: clusterNodeInfo.nodeId || nodeId,
                    host: clusterNodeInfo.host,
                    status: isOnline ? 'ONLINE' : 'OFFLINE' as NodeStatus,
                    lastHeartbeat,
                    memory: {
                        used: clusterNodeInfo.memory?.used || 0,
                        total: clusterNodeInfo.memory?.total || 0,
                        usageRate: clusterNodeInfo.memory?.total ? ((clusterNodeInfo.memory.used / clusterNodeInfo.memory.total) * 100) : 0
                    },
                    cpu: {
                        processors: clusterNodeInfo.cpu?.processors || 0,
                        loadAverage: clusterNodeInfo.cpu?.loadAverage || 0
                    }
                };
            });
            setNodes(nodeListItems);

            // 计算集群统计信息
            const stats: ClusterStatistics = {
                totalNodes: nodeListItems.length,
                onlineNodes: nodeListItems.filter(node => node.status === 'ONLINE').length,
                offlineNodes: nodeListItems.filter(node => node.status === 'OFFLINE').length,
                totalMemory: nodeListItems.reduce((sum, node) => sum + (node.memory.total || 0), 0),
                usedMemory: nodeListItems.reduce((sum, node) => sum + (node.memory.used || 0), 0),
                averageCpuLoad: nodeListItems.length > 0
                    ? nodeListItems.reduce((sum, node) => sum + (node.cpu.loadAverage || 0), 0) / nodeListItems.length
                    : 0
            };
            setClusterStats(stats);
        } catch (error) {
            message.error('加载集群数据失败');
            console.error('Failed to load cluster data:', error);
        } finally {
            setLoading(false);
        }
    };

    // 加载节点详情
    const loadNodeDetail = async (nodeId: string) => {
        try {
            const nodesData = await clusterApi.getAllNodes();
            const detail = nodesData[nodeId];
            if (detail) {
                // 保存完整的节点详情数据（包含clusterNodeInfo和taskStage）
                setNodeDetail(detail);
            }
        } catch (error) {
            message.error('加载节点详情失败');
            console.error('Failed to load node detail:', error);
        }
    };

    useEffect(() => {
        loadClusterData();
    }, []);

    const statusMap = {
        ONLINE: {text: '在线', color: 'success', icon: <CheckCircleOutlined/>},
        OFFLINE: {text: '离线', color: 'error', icon: <CloseCircleOutlined/>},
        UNSTABLE: {text: '不稳定', color: 'warning', icon: <WarningOutlined/>},
    };

    const columns = [
        {
            title: '节点ID',
            dataIndex: 'nodeId',
            key: 'nodeId',
            width: 120,
            ellipsis: true,
        },
        {
            title: '主机地址',
            dataIndex: 'host',
            key: 'host',
            width: 120,
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 100,
            render: (status: NodeStatus) => (
                <Tag
                    color={statusMap[status]?.color || 'default'}
                    icon={statusMap[status]?.icon}
                >
                    {statusMap[status]?.text || status}
                </Tag>
            ),
        },
        {
            title: '内存使用率',
            dataIndex: 'memory',
            key: 'memory',
            width: 150,
            render: (memory: NodeListItem['memory']) => (
                <div>
                    <Progress
                        percent={Math.round(memory.usageRate)}
                        size="small"
                        strokeColor={memory.usageRate > 80 ? '#ff4d4f' : memory.usageRate > 60 ? '#faad14' : '#52c41a'}
                    />
                    <div style={{fontSize: '12px', color: '#666'}}>
                        {formatBytes(memory.used)} / {formatBytes(memory.total)}
                    </div>
                </div>
            ),
        },
        {
            title: 'CPU负载',
            dataIndex: 'cpu',
            key: 'cpu',
            width: 120,
            render: (cpu: NodeListItem['cpu']) => {
                const usageRate = cpu.processors ? (cpu.loadAverage / cpu.processors) * 100 : 0;
                return (
                    <div>
                        <Progress
                            percent={Math.round(usageRate)}
                            size="small"
                            strokeColor={usageRate > 80 ? '#ff4d4f' : usageRate > 60 ? '#faad14' : '#52c41a'}
                        />
                        <div style={{fontSize: '12px', color: '#666'}}>
                            {cpu.loadAverage.toFixed(2)} / {cpu.processors}
                        </div>
                    </div>
                );
            },
        },
        {
            title: 'CPU核',
            dataIndex: 'cpu',
            key: 'processors',
            width: 80,
            render: (cpu: NodeListItem['cpu']) => (
                <Tag color="blue">{cpu.processors}</Tag>
            ),
        },
        {
            title: '最后心跳',
            dataIndex: 'lastHeartbeat',
            key: 'lastHeartbeat',
            width: 120,
        },
        {
            title: '操作',
            key: 'action',
            width: 200,
            render: (_: unknown, record: NodeListItem) => (
                <Space.Compact size="small">
                    <Button
                        type="link"
                        icon={<EyeOutlined/>}
                        onClick={() => handleViewDetail(record.nodeId)}
                    >
                        详情
                    </Button>
                    {record.status === 'ONLINE' ? (
                        <Popconfirm
                            title="确定要禁用这个节点吗？"
                            onConfirm={() => handleDisableNode(record.nodeId)}
                            okText="确定"
                            cancelText="取消"
                        >
                            <Button
                                type="link"
                                danger
                                icon={<PauseCircleOutlined/>}
                            >
                                禁用
                            </Button>
                        </Popconfirm>
                    ) : (
                        <Button
                            type="link"
                            icon={<PlayCircleOutlined/>}
                            onClick={() => handleEnableNode(record.nodeId)}
                        >
                            启用
                        </Button>
                    )}
                    <Popconfirm
                        title="确定要重启这个节点吗？"
                        onConfirm={() => handleRestartNode(record.nodeId)}
                        okText="确定"
                        cancelText="取消"
                    >
                        <Button
                            type="link"
                            icon={<ReloadOutlined/>}
                        >
                            重启
                        </Button>
                    </Popconfirm>
                </Space.Compact>
            ),
        },
    ];

    const handleViewDetail = (nodeId: string) => {
        setSelectedNodeId(nodeId);
        setDetailModalVisible(true);
        loadNodeDetail(nodeId);
    };

    const handleRestartNode = async (nodeId: string) => {
        try {
            await clusterApi.restartNode(nodeId);
            message.success('重启节点指令已发送');
            setTimeout(() => loadClusterData(), 2000); // 2秒后重新加载数据
        } catch {
            message.error('重启节点失败');
        }
    };

    const handleDisableNode = async (nodeId: string) => {
        try {
            await clusterApi.disableNode(nodeId);
            message.success('禁用节点成功');
            loadClusterData();
        } catch {
            message.error('禁用节点失败');
        }
    };

    const handleEnableNode = async (nodeId: string) => {
        try {
            await clusterApi.enableNode(nodeId);
            message.success('启用节点成功');
            loadClusterData();
        } catch {
            message.error('启用节点失败');
        }
    };

    // 格式化字节大小
    const formatBytes = (bytes: number): string => {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    };

    return (
        <div>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24}}>
                <Title level={2}>集群管理</Title>
                <Space>
                    <Button
                        icon={<ReloadOutlined/>}
                        onClick={loadClusterData}
                    >
                        刷新
                    </Button>
                </Space>
            </div>

            {/* 集群统计信息 */}
            {clusterStats && (
                <Row gutter={16} style={{marginBottom: 24}}>
                    <Col span={6}>
                        <Card>
                            <Statistic
                                title="总节点数"
                                value={clusterStats.totalNodes}
                                suffix={`/ ${clusterStats.onlineNodes} 在线`}
                            />
                        </Card>
                    </Col>
                    <Col span={6}>
                        <Card>
                            <Statistic
                                title="内存使用"
                                value={Math.round((clusterStats.usedMemory / clusterStats.totalMemory) * 100)}
                                suffix="%"
                            />
                        </Card>
                    </Col>
                    <Col span={6}>
                        <Card>
                            <Statistic
                                title="平均CPU负载"
                                value={clusterStats.averageCpuLoad}
                                precision={2}
                            />
                        </Card>
                    </Col>
                    <Col span={6}>
                        <Card>
                            <Statistic
                                title="总内存"
                                value={formatBytes(clusterStats.totalMemory)}
                            />
                        </Card>
                    </Col>
                </Row>
            )}

            <Card>
                <Spin spinning={loading}>
                    <Table
                        columns={columns}
                        dataSource={nodes}
                        rowKey="nodeId"
                        pagination={{
                            pageSize: 10,
                            showSizeChanger: true,
                            showQuickJumper: true,
                            showTotal: (total) => `共 ${total} 个节点`,
                        }}
                    />
                </Spin>
            </Card>

            {/* 节点详情模态框 */}
            <Modal
                title={`节点详情 - ${selectedNodeId}`}
                open={detailModalVisible}
                onCancel={() => setDetailModalVisible(false)}
                footer={null}
                width={800}
            >
                {nodeDetail && (
                    <>
                        <Descriptions bordered column={2}>
                            <Descriptions.Item label="节点ID">
                                {nodeDetail.clusterNodeInfo?.nodeId || selectedNodeId}
                            </Descriptions.Item>
                            <Descriptions.Item label="主机地址">
                                {nodeDetail.clusterNodeInfo?.host || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="最后更新时间">
                                {nodeDetail.clusterNodeInfo?.timestamp ? dayjs(nodeDetail.clusterNodeInfo.timestamp).format('YYYY-MM-DD HH:mm:ss') : '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="状态">
                                {nodeDetail.alive ? (
                                    <Tag color="success">在线</Tag>
                                ) : (
                                    <Tag color="error">离线</Tag>
                                )}
                            </Descriptions.Item>
                            <Descriptions.Item label="CPU核心数" span={2}>
                                {nodeDetail.clusterNodeInfo?.cpu?.processors || 0}
                            </Descriptions.Item>
                            <Descriptions.Item label="CPU负载" span={2}>
                                <Progress
                                    percent={nodeDetail.clusterNodeInfo?.cpu?.processors ? Math.round((nodeDetail.clusterNodeInfo.cpu.loadAverage / nodeDetail.clusterNodeInfo.cpu.processors) * 100) : 0}
                                    format={percent => `${percent}%`}
                                    strokeColor={nodeDetail.clusterNodeInfo?.cpu?.processors ?
                                        (nodeDetail.clusterNodeInfo.cpu.loadAverage / nodeDetail.clusterNodeInfo.cpu.processors) > 0.8 ? '#ff4d4f' :
                                            (nodeDetail.clusterNodeInfo.cpu.loadAverage / nodeDetail.clusterNodeInfo.cpu.processors) > 0.6 ? '#faad14' : '#52c41a'
                                        : '#52c41a'
                                    }
                                />
                                当前负载: {nodeDetail.clusterNodeInfo?.cpu?.loadAverage?.toFixed(2) || 0} / {nodeDetail.clusterNodeInfo?.cpu?.processors || 0}
                            </Descriptions.Item>
                            <Descriptions.Item label="内存最大容量" span={2}>
                                {formatBytes(nodeDetail.clusterNodeInfo?.memory?.max || 0)}
                            </Descriptions.Item>
                            <Descriptions.Item label="内存使用" span={2}>
                                <div style={{marginBottom: 8}}>
                                    <Progress
                                        percent={nodeDetail.clusterNodeInfo?.memory?.total ? Math.round((nodeDetail.clusterNodeInfo.memory.used / nodeDetail.clusterNodeInfo.memory.total) * 100) : 0}
                                        strokeColor={
                                            nodeDetail.clusterNodeInfo?.memory?.total ?
                                                (nodeDetail.clusterNodeInfo.memory.used / nodeDetail.clusterNodeInfo.memory.total) > 0.8 ? '#ff4d4f' :
                                                    (nodeDetail.clusterNodeInfo.memory.used / nodeDetail.clusterNodeInfo.memory.total) > 0.6 ? '#faad14' : '#52c41a'
                                                : '#52c41a'
                                        }
                                    />
                                </div>
                                <div>
                                    <span>已用: {formatBytes(nodeDetail.clusterNodeInfo?.memory?.used || 0)}</span>
                                    <span style={{margin: '0 8px'}}>/</span>
                                    <span>总量: {formatBytes(nodeDetail.clusterNodeInfo?.memory?.total || 0)}</span>
                                    <span style={{margin: '0 8px'}}>/</span>
                                    <span>可用: {formatBytes(nodeDetail.clusterNodeInfo?.memory?.free || 0)}</span>
                                    {nodeDetail.clusterNodeInfo?.memory?.max && nodeDetail.clusterNodeInfo.memory.max !== nodeDetail.clusterNodeInfo.memory.total && (
                                        <span style={{margin: '0 8px'}}>/</span>
                                    )}
                                    {nodeDetail.clusterNodeInfo?.memory?.max && nodeDetail.clusterNodeInfo.memory.max !== nodeDetail.clusterNodeInfo.memory.total && (
                                        <span>最大: {formatBytes(nodeDetail.clusterNodeInfo.memory.max)}</span>
                                    )}
                                </div>
                            </Descriptions.Item>
                        </Descriptions>

                        {/* 任务状态展示 */}
                        {nodeDetail.taskStage && Object.keys(nodeDetail.taskStage).length > 0 && (
                            <div style={{marginTop: 24}}>
                                <Typography.Title level={4}>任务状态</Typography.Title>
                                <Descriptions bordered column={1}>
                                    {Object.entries(nodeDetail.taskStage).map(([taskId, status]) => (
                                        <Descriptions.Item key={taskId} label={`任务 ${taskId}`}>
                                            <Tag
                                                color={
                                                    status === 'ONGOING' ? 'blue' :
                                                        status === 'START' ? 'blue' :
                                                            status === 'CONNECTING' ? 'blue' :
                                                                status === 'INIT_PUB_CLIENT' ? 'blue' :
                                                                    status === 'INIT_SUB_CLIENT' ? 'blue' :
                                                                        status === 'INIT' ? 'orange' :
                                                                            status === 'SHUTDOWN' ? 'default' : 'gray'
                                                }
                                            >
                                                {TaskStatusText[status as keyof typeof TaskStatusValues] || status}
                                            </Tag>
                                        </Descriptions.Item>
                                    ))}
                                </Descriptions>
                            </div>
                        )}

                        {(!nodeDetail.taskStage || Object.keys(nodeDetail.taskStage).length === 0) && (
                            <div style={{marginTop: 24, textAlign: 'center', padding: 20, color: '#999'}}>
                                该节点当前没有运行中的任务
                            </div>
                        )}
                    </>
                )}
            </Modal>
        </div>
    );
};

export default ClusterManagement;