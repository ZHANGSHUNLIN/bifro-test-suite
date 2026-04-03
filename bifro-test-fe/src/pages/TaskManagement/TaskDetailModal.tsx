import React, {useEffect, useState} from 'react';
import {Modal, Descriptions, Tag, Row, Col, Statistic, Table, Alert, Tabs, Button, Space, Collapse} from 'antd';
import {useTaskData} from './hooks';
import {taskApi} from '../../services/taskApi';
import taskGroupApi from '../../services/taskGroupApi';
import type {TaskDetailResponse, TaskReport} from '../../types/task';
import type {TaskGroup} from '../../types/taskGroup';

const {TabPane} = Tabs;
const {Panel} = Collapse;

interface TaskDetailModalProps {
    visible: boolean;
    id: string | null;
    taskId: string | null;
    onClose: () => void;
    onTaskDetailLoaded?: (detail: TaskDetailResponse) => void;
}

const TaskDetailModal: React.FC<TaskDetailModalProps> = ({
                                                             visible,
                                                             id,
                                                             taskId,
                                                             onClose,
                                                             onTaskDetailLoaded
                                                         }) => {
    const [taskDetail, setTaskDetail] = useState<TaskDetailResponse | null>(null);
    const {loadTaskDetail} = useTaskData();
    const [groupOptions, setGroupOptions] = useState<{ label: string; value: string }[]>([]);

    // 任务报告详情弹窗状态
    const [reportModalVisible, setReportModalVisible] = useState(false);
    const [reportData, setReportData] = useState<TaskReport[]>([]);
    const [reportLoading, setReportLoading] = useState(false);
    const [currentNodeId, setCurrentNodeId] = useState<string>('');
    const [currentTaskId, setCurrentTaskId] = useState<string>('');

    // 分页状态
    const [pagination, setPagination] = useState({
        current: 1,
        pageSize: 10,
        total: 0,
    });

    // 加载任务详情
    useEffect(() => {
        if (visible && id) {
            loadTaskDetail(id).then(detail => {
                setTaskDetail(detail);
                onTaskDetailLoaded?.(detail);
            }).catch(error => {
                console.error('Failed to load task detail:', error);
            });
        }
    }, [visible, id]);

    // 加载分组选项
    useEffect(() => {
        if (visible) {
            taskGroupApi.getAllGroupsForSelect().then(allGroups => {
                const options = allGroups.map((g: TaskGroup) => ({
                    label: g.name,
                    value: g.id
                }));
                setGroupOptions(options);
            }).catch(error => {
                console.error('加载分组选项失败:', error);
            });
        }
    }, [visible]);

    // 加载任务报告详情
    const loadTaskReport = async (nodeId: string, taskId: string, page: number = 1, pageSize: number = 10) => {
        setReportLoading(true);
        try {
            // 后端返回 ApiResponse<PageInfo<Report>>，request.ts 已提取 data 部分
            const pageInfo = await taskApi.getTaskReport(nodeId, taskId, page, pageSize);
            setReportData(pageInfo.content || []);
            setPagination({
                current: pageInfo.number + 1 || page,
                pageSize: pageInfo.size || pageSize,
                total: pageInfo.totalElements || 0,
            });
        } catch (error) {
            console.error('Failed to load task report:', error);
            setReportData([]);
        } finally {
            setReportLoading(false);
        }
    };

    // 打开报告详情弹窗
    const handleShowReport = (nodeId: string) => {
        if (id) {
            setCurrentNodeId(nodeId);
            setCurrentTaskId(id);
            loadTaskReport(nodeId, id);
            setReportModalVisible(true);
        }
    };

    // 分页变化处理
    const handlePageChange = (page: number, pageSize: number) => {
        loadTaskReport(currentNodeId, currentTaskId, page, pageSize);
    };

    // 状态映射
    const getStatusColor = (status: string) => {
        const statusColors: Record<string, string> = {
            'INIT': 'default',
            'START': 'processing',
            'CONNECTING': 'processing',
            'INIT_PUB_CLIENT': 'processing',
            'INIT_SUB_CLIENT': 'processing',
            'ONGOING': 'processing',
            'SHUTDOWN': 'success',
            'STOPPED': 'warning',
        };
        return statusColors[status] || 'default';
    };

    const getStatusText = (status: string) => {
        const statusTexts: Record<string, string> = {
            'INIT': '已创建',
            'START': '启动中',
            'CONNECTING': '连接中',
            'INIT_PUB_CLIENT': '初始化发布端',
            'INIT_SUB_CLIENT': '初始化订阅端',
            'ONGOING': '运行中',
            'SHUTDOWN': '已完成',
            'STOPPED': '已停止',
        };
        return statusTexts[status] || status;
    };

    // 格式化统计结果显示
    const formatStatsResult = (stats: any) => {
        if (!stats) return '-';

        // 处理连接统计结果 (嵌套在 actualResult 中)
        if (stats.actualResult) {
            const actual = stats.actualResult;
            return (
                <div style={{fontSize: '12px'}}>
                    <div>expectConnCount: {stats.expectConnCount || 0}</div>
                    <div>actualConnCount: {stats.actualConnCount || 0}</div>
                    <div>expectConnQps: {stats.expectConnQps?.toFixed(2) || 0}</div>
                    <div>actualConnQps: {stats.actualConnQps?.toFixed(2) || 0}</div>
                    <div>connectFailCount: {stats.connectFailCount || 0}</div>
                    <div style={{marginTop: '8px', borderTop: '1px solid #ddd', paddingTop: '8px'}}>
                        <div><strong>Latency Stats:</strong></div>
                        <div>count: {actual.count?.toLocaleString() || 0}</div>
                        <div>qps: {actual.qps?.toFixed(2) || 0}</div>
                        <div>meanLatency: {actual.meanLatency?.toFixed(2) || 0}ms</div>
                        <div>medianLatency: {actual.medianLatency?.toFixed(2) || 0}ms</div>
                        <div>p95Latency: {actual.p95Latency?.toFixed(2) || 0}ms</div>
                        <div>p99Latency: {actual.p99Latency?.toFixed(2) || 0}ms</div>
                        <div>p999Latency: {actual.p999Latency?.toFixed(2) || 0}ms</div>
                        <div>maxLatency: {actual.maxLatency?.toFixed(2) || 0}ms</div>
                        <div>minLatency: {actual.minLatency?.toFixed(2) || 0}ms</div>
                    </div>
                </div>
            );
        }

        // 处理普通统计结果
        const {count, qps, meanLatency, medianLatency, p95Latency, p99Latency, maxLatency, minLatency} = stats;
        return (
            <div style={{fontSize: '12px'}}>
                <div>count: {count?.toLocaleString() || 0}</div>
                <div>qps: {qps?.toFixed(2) || 0}</div>
                <div>meanLatency: {meanLatency?.toFixed(2) || 0}ms</div>
                <div>medianLatency: {medianLatency?.toFixed(2) || 0}ms</div>
                <div>p95Latency: {p95Latency?.toFixed(2) || 0}ms</div>
                <div>p99Latency: {p99Latency?.toFixed(2) || 0}ms</div>
                <div>maxLatency: {maxLatency?.toFixed(2) || 0}ms</div>
                <div>minLatency: {minLatency?.toFixed(2) || 0}ms</div>
            </div>
        );
    };

    // 展开渲染更多统计数据
    const expandedRowRender = (record: TaskReport) => {
        const taskType = taskDetail?.mainTask?.taskType;
        return (
            <Collapse ghost>
                {record.statsBasicResult && (
                    <Panel header="Basic Stats" key="basic">
            <pre style={{fontSize: '12px', maxHeight: '300px', overflow: 'auto'}}>
              {JSON.stringify(record.statsBasicResult, null, 2)}
            </pre>
                    </Panel>
                )}
                {taskType === 'PUBSUB' && record.statsSubResult && (
                    <Panel header="Sub Stats" key="sub">
            <pre style={{fontSize: '12px', maxHeight: '300px', overflow: 'auto'}}>
              {JSON.stringify(record.statsSubResult, null, 2)}
            </pre>
                    </Panel>
                )}
                {taskType === 'PUBSUB' && record.statsPubResult && (
                    <Panel header="Pub Stats" key="pub">
            <pre style={{fontSize: '12px', maxHeight: '300px', overflow: 'auto'}}>
              {JSON.stringify(record.statsPubResult, null, 2)}
            </pre>
                    </Panel>
                )}
                {taskType === 'CONN' && record.statsConnResult && (
                    <Panel header="Conn Stats" key="conn">
            <pre style={{fontSize: '12px', maxHeight: '300px', overflow: 'auto'}}>
              {JSON.stringify(record.statsConnResult, null, 2)}
            </pre>
                    </Panel>
                )}
            </Collapse>
        );
    };

    return (
        <>
            <Modal
                title={`任务详情 - ${taskId}`}
                open={visible}
                onCancel={onClose}
                footer={null}
                width={1000}
            >
                {taskDetail && (
                    <Tabs defaultActiveKey="basic">
                        <TabPane tab="基本信息" key="basic">
                            {taskDetail.mainTask && (
                                <Descriptions bordered column={2}>
                                    <Descriptions.Item label="任务名称">{taskDetail.taskName || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="任务ID">{taskDetail.taskId}</Descriptions.Item>
                                    <Descriptions.Item
                                        label="任务类型">{taskDetail.mainTask.taskType}</Descriptions.Item>
                                    <Descriptions.Item label="协议">{taskDetail.mainTask.protocol}</Descriptions.Item>
                                    <Descriptions.Item label="服务器地址">
                                        {taskDetail.brokers?.map(b => `${b.host}:${b.port}`).join(', ') || '-'}
                                    </Descriptions.Item>
                                    <Descriptions.Item label="任务分组">
                                        {taskDetail.mainTask?.group ? groupOptions.find(opt => opt.value === taskDetail.mainTask?.group)?.label || taskDetail.mainTask?.group : '-'}
                                    </Descriptions.Item>
                                    <Descriptions.Item
                                        label="客户端数量">{taskDetail.mainTask.totalClientCount}</Descriptions.Item>
                                    <Descriptions.Item
                                        label="连接速率">{taskDetail.mainTask.connectRate || 1} 个/秒</Descriptions.Item>
                                    <Descriptions.Item
                                        label="断开速率">{taskDetail.mainTask.disconnectRate || 2000} 个/秒</Descriptions.Item>
                                    <Descriptions.Item
                                        label="Fan Out">{taskDetail.mainTask.fanOut || 1}</Descriptions.Item>
                                    <Descriptions.Item
                                        label="Fan In">{taskDetail.mainTask.fanIn || 1}</Descriptions.Item>
                                    <Descriptions.Item
                                        label="主题">{taskDetail.mainTask.topic || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="QoS">{taskDetail.mainTask.qos}</Descriptions.Item>
                                    <Descriptions.Item
                                        label="消息大小">{taskDetail.mainTask.messageSize || 32} 字节</Descriptions.Item>
                                    <Descriptions.Item
                                        label="发布间隔">{taskDetail.mainTask.pubIntervalInMs || 10000} 毫秒</Descriptions.Item>
                                    <Descriptions.Item
                                        label="测试时长">{taskDetail.mainTask.stressDurationInSec || 60} 秒</Descriptions.Item>
                                    <Descriptions.Item label="Clean Session">
                                        {taskDetail.mainTask.cleanSession !== false ? '是' : '否'}
                                    </Descriptions.Item>
                                    <Descriptions.Item label="Retain">
                                        {taskDetail.mainTask.retain ? '是' : '否'}
                                    </Descriptions.Item>
                                    <Descriptions.Item label="MQTT 5.0">
                                        {taskDetail.mainTask.mqtt5 ? '是' : '否'}
                                    </Descriptions.Item>
                                    <Descriptions.Item label="认证类型">
                                        {taskDetail.mainTask.authType === 'normal' ? '普通' :
                                            taskDetail.mainTask.authType === 'byoc' ? 'BYOC' :
                                                taskDetail.mainTask.authType === 'iotCore' ? 'IoT Core' :
                                                    taskDetail.mainTask.authType || 'normal'}
                                    </Descriptions.Item>
                                    {taskDetail.mainTask.authType === 'normal' && (
                                        <>
                                            <Descriptions.Item
                                                label="用户名">{taskDetail.mainTask.username || '-'}</Descriptions.Item>
                                            <Descriptions.Item
                                                label="密码">{taskDetail.mainTask.password ? '***' : '-'}</Descriptions.Item>
                                        </>
                                    )}
                                    {taskDetail.mainTask.authType === 'byoc' && (
                                        <>
                                            <Descriptions.Item
                                                label="租户ID">{taskDetail.mainTask.tenantId || '-'}</Descriptions.Item>
                                            <Descriptions.Item
                                                label="Thing ID前缀">{taskDetail.mainTask.thingIdPrefix || 'demo_'}</Descriptions.Item>
                                            <Descriptions.Item
                                                label="Thing ID起始值">{taskDetail.mainTask.thingIdStartAt || 0}</Descriptions.Item>
                                        </>
                                    )}
                                    <Descriptions.Item label="异常终止">
                                        {taskDetail.mainTask.exceptionEnds !== false ? '是' : '否'}
                                    </Descriptions.Item>
                                    {taskDetail.mainTask.willConfig && taskDetail.mainTask.willConfig.willFlag && (
                                        <>
                                            <Descriptions.Item label="Will Topic">
                                                {taskDetail.mainTask.willConfig.willTopic || '-'}
                                            </Descriptions.Item>
                                            <Descriptions.Item label="Will Message">
                                                {taskDetail.mainTask.willConfig.willMessage || '-'}
                                            </Descriptions.Item>
                                            <Descriptions.Item label="Will QoS">
                                                {taskDetail.mainTask.willConfig.willQos || 0}
                                            </Descriptions.Item>
                                            <Descriptions.Item label="Will Retain">
                                                {taskDetail.mainTask.willConfig.willRetain ? '是' : '否'}
                                            </Descriptions.Item>
                                            <Descriptions.Item label="Will Message Length">
                                                {taskDetail.mainTask.willConfig.willMessageLen || '-'}
                                            </Descriptions.Item>
                                        </>
                                    )}
                                </Descriptions>
                            )}
                            {!taskDetail.success && (
                                <Alert description={taskDetail.message} type="error" showIcon/>
                            )}
                        </TabPane>

                        <TabPane tab="统计信息" key="statistics">
                            {taskDetail.statistics && (
                                <Row gutter={16}>
                                    <Col span={6}>
                                        <Statistic title="总节点数" value={taskDetail.statistics.totalNodes || 0}/>
                                    </Col>
                                    <Col span={6}>
                                        <Statistic title="总分配客户端"
                                                   value={taskDetail.statistics.totalAssignedClients || 0}/>
                                    </Col>
                                    <Col span={6}>
                                        <Statistic title="最小客户端/节点"
                                                   value={taskDetail.statistics.minClientsPerNode || 0}/>
                                    </Col>
                                    <Col span={6}>
                                        <Statistic title="最大客户端/节点"
                                                   value={taskDetail.statistics.maxClientsPerNode || 0}/>
                                    </Col>
                                </Row>
                            )}
                        </TabPane>

                        <TabPane tab="子任务" key="subtasks">
                            {taskDetail.subTasks && Object.keys(taskDetail.subTasks).length > 0 ? (
                                <Table
                                    dataSource={Object.entries(taskDetail.subTasks).map(([nodeId, task]) => ({
                                        key: nodeId,
                                        nodeId,
                                        ...task,
                                    }))}
                                    columns={[
                                        {title: '节点ID', dataIndex: 'nodeId', key: 'nodeId'},
                                        {title: '任务类型', dataIndex: 'taskType', key: 'taskType'},
                                        {title: '客户端数量', dataIndex: 'totalClientCount', key: 'totalClientCount'},
                                        {
                                            title: '状态',
                                            dataIndex: 'taskWorkStage',
                                            key: 'taskWorkStage',
                                            render: (status: string, record: any) => (
                                                <Space>
                                                    <Tag color={getStatusColor(status)}>
                                                        {getStatusText(status)}
                                                    </Tag>
                                                    <Button
                                                        type="link"
                                                        size="small"
                                                        onClick={() => handleShowReport(record.nodeId)}
                                                    >
                                                        详情
                                                    </Button>
                                                </Space>
                                            ),
                                        },
                                    ]}
                                    pagination={false}
                                    size="small"
                                />
                            ) : (
                                <div style={{textAlign: 'center', color: '#999'}}>暂无子任务</div>
                            )}
                        </TabPane>
                    </Tabs>
                )}
            </Modal>

            {/* 任务报告详情弹窗 */}
            <Modal
                title={`任务报告详情 - 节点: ${currentNodeId}`}
                open={reportModalVisible}
                onCancel={() => setReportModalVisible(false)}
                footer={null}
                width={900}
            >
                <Table
                    dataSource={reportData}
                    loading={reportLoading}
                    expandable={{
                        expandedRowRender,
                        rowExpandable: (record: TaskReport) => {
                            const taskType = taskDetail?.mainTask?.taskType;
                            if (taskType === 'PUBSUB') {
                                return !!(record.statsBasicResult || record.statsSubResult || record.statsPubResult);
                            } else if (taskType === 'CONN') {
                                return !!(record.statsBasicResult || record.statsConnResult);
                            }
                            return !!(record.statsBasicResult || record.statsSubResult || record.statsPubResult || record.statsConnResult);
                        },
                    }}
                    columns={(() => {
                        const taskType = taskDetail?.mainTask?.taskType;
                        const baseColumns = [
                            {
                                title: '任务阶段',
                                dataIndex: 'taskStage',
                                key: 'taskStage',
                                width: 120,
                            },
                            {
                                title: '创建时间',
                                dataIndex: 'createTime',
                                key: 'createTime',
                                width: 180,
                            },
                            {
                                title: 'Basic Stats',
                                key: 'statsBasicResult',
                                width: 200,
                                render: (_: any, record: TaskReport) => formatStatsResult(record.statsBasicResult),
                            },
                        ];

                        if (taskType === 'PUBSUB') {
                            return [
                                ...baseColumns,
                                {
                                    title: 'Sub Stats',
                                    key: 'statsSubResult',
                                    width: 200,
                                    render: (_: any, record: TaskReport) => formatStatsResult(record.statsSubResult),
                                },
                                {
                                    title: 'Pub Stats',
                                    key: 'statsPubResult',
                                    width: 200,
                                    render: (_: any, record: TaskReport) => formatStatsResult(record.statsPubResult),
                                },
                            ];
                        } else if (taskType === 'CONN') {
                            return [
                                ...baseColumns,
                                {
                                    title: 'Conn Stats',
                                    key: 'statsConnResult',
                                    width: 200,
                                    render: (_: any, record: TaskReport) => formatStatsResult(record.statsConnResult),
                                },
                            ];
                        }

                        return [
                            ...baseColumns,
                            {
                                title: 'Sub Stats',
                                key: 'statsSubResult',
                                width: 200,
                                render: (_: any, record: TaskReport) => formatStatsResult(record.statsSubResult),
                            },
                            {
                                title: 'Pub Stats',
                                key: 'statsPubResult',
                                width: 200,
                                render: (_: any, record: TaskReport) => formatStatsResult(record.statsPubResult),
                            },
                            {
                                title: 'Conn Stats',
                                key: 'statsConnResult',
                                width: 200,
                                render: (_: any, record: TaskReport) => formatStatsResult(record.statsConnResult),
                            },
                        ];
                    })()}
                    pagination={{
                        current: pagination.current,
                        pageSize: pagination.pageSize,
                        total: pagination.total,
                        onChange: handlePageChange,
                        showSizeChanger: true,
                        showQuickJumper: true,
                        showTotal: (total) => `共 ${total} 条`,
                    }}
                    rowKey="id"
                    size="small"
                    scroll={{x: 1000}}
                />
            </Modal>
        </>
    );
};

export default TaskDetailModal;