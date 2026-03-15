import React, { useState, useEffect } from 'react';
import {
    Card,
    Table,
    Button,
    Space,
    Modal,
    Form,
    Input,
    InputNumber,
    Switch,
    message,
    Popconfirm,
    Tag,
    Typography,
    Spin,
    Descriptions,
    Row,
    Col
} from 'antd';
import {
    PlusOutlined,
    EditOutlined,
    DeleteOutlined,
    EyeOutlined,
    ReloadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import mqttBrokerApi from '../services/mqttBrokerApi';
import type {
    BrokerListItem,
    MqttBrokerConfig
} from '../types/mqttBroker';

const { Title } = Typography;

const MqttBrokerManagement: React.FC = () => {
    const [brokers, setBrokers] = useState<BrokerListItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [isModalVisible, setIsModalVisible] = useState(false);
    const [detailModalVisible, setDetailModalVisible] = useState(false);
    const [editingBroker, setEditingBroker] = useState<BrokerListItem | null>(null);
    const [selectedBrokerId, setSelectedBrokerId] = useState<string | null>(null);
    const [brokerDetail, setBrokerDetail] = useState<MqttBrokerConfig | null>(null);
    const [form] = Form.useForm();

    // 加载 Broker 列表
    const loadBrokers = async () => {
        setLoading(true);
        try {
            const pageInfo = await mqttBrokerApi.getAllBrokers()
            const brokerListItems: BrokerListItem[] = pageInfo.content.map((config: BrokerListItem) => ({
                brokerId: config.brokerId || '',
                id: config.id,
                name: config.name,
                host: config.host,
                port: config.port,
                description: config.description || '',
                enabled: config.enabled,
                sslEnabled: config.sslEnabled || false,
                lastHealthCheck: config.lastHealthCheck || '',
            }));
            setBrokers(brokerListItems);
        } catch (error) {
            message.error('加载 Broker 列表失败');
        } finally {
            setLoading(false);
        }
    };

    // 加载 Broker 详情
    const loadBrokerDetail = async (id: string) => {
        try {
            const detail = await mqttBrokerApi.getBrokerDetails(id);
            setBrokerDetail(detail);
        } catch (error) {
            message.error('加载 Broker 详情失败');
            console.error('Failed to load broker detail:', error);
        }
    };


    useEffect(() => {
        loadBrokers();
    }, []);


    const columns = [
        {
            title: '名称',
            dataIndex: 'name',
            key: 'name',
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
            title: '端口',
            dataIndex: 'port',
            key: 'port',
            width: 80,
            render: (port: number) => <Tag color="blue">{port}</Tag>,
        },
        {
            title: '是否启用',
            dataIndex: 'enabled',
            key: 'enabled',
            width: 100,
            render: (enabled: boolean) => (
                <Tag color={enabled ? 'green' : 'red'}>
                    {enabled ? '启用' : '禁用'}
                </Tag>
            ),
        },
        {
            title: '备注',
            dataIndex: 'description',
            key: 'description',
            width: 150,
            ellipsis: true,
            render: (description: string) => description || '-',
        },
        {
            title: '操作',
            key: 'action',
            width: 280,
            render: (_: unknown, record: BrokerListItem) => (
                <Space.Compact size="small">
                    <Button
                        type="link"
                        icon={<EyeOutlined />}
                        onClick={() => handleViewDetail(record.id,record.brokerId)}
                    >
                        详情
                    </Button>
                    <Button
                        type="link"
                        icon={<EditOutlined />}
                        onClick={() => handleEdit(record)}
                    >
                        编辑
                    </Button>
                    <Button
                        type="link"
                        danger={record.enabled}
                        onClick={() => handleToggleStatus(record.id, !record.enabled)}
                    >
                        {record.enabled ? '禁用' : '启用'}
                    </Button>
                    <Popconfirm
                        title="确定要删除这个 Broker 吗？"
                        onConfirm={() => handleDelete(record.id)}
                        okText="确定"
                        cancelText="取消"
                    >
                        <Button
                            type="link"
                            danger
                            icon={<DeleteOutlined />}
                        >
                            删除
                        </Button>
                    </Popconfirm>
                </Space.Compact>
            ),
        },
    ];

    const handleAdd = () => {
        setEditingBroker(null);
        form.resetFields();
        setIsModalVisible(true);
    };

    const handleEdit = (broker: BrokerListItem) => {
        setEditingBroker(broker);
        form.setFieldsValue({
            name: broker.name,
            host: broker.host,
            port: broker.port,
            description: broker.description,
            enabled: broker.enabled,
        });
        setIsModalVisible(true);
    };

    const handleViewDetail = (id: string, brokerId: string) => {
        setSelectedBrokerId(brokerId);
        setDetailModalVisible(true);
        loadBrokerDetail(id);
    };

    const handleDelete = async (id: string) => {
        try {
            await mqttBrokerApi.deleteBroker(id);
            message.success('删除成功');
            await loadBrokers();
        } catch {
            message.error('删除失败');
        }
    };

    const handleToggleStatus = async (brokerId: string, enabled: boolean) => {
        try {
            await mqttBrokerApi.toggleBrokerStatus(brokerId, enabled);
            message.success(`${enabled ? '启用' : '禁用'}成功`);
            await loadBrokers();
        } catch {
            message.error(`${enabled ? '启用' : '禁用'}失败`);
        }
    };

    const handleModalOk = async () => {
        try {
            const values = await form.validateFields();
            const brokerRequest = {
                id: values.id,
                name: values.name,
                host: values.host,
                port: values.port,
                description: values.description,
                enabled: values.enabled !== false,
                username: values.username,
                password: values.password,
                sslEnabled: values.sslEnabled || false,
                keepAliveSeconds: values.keepAliveSeconds || 60,
                connectionTimeoutSeconds: values.connectionTimeoutSeconds || 10,
                maxConnections: values.maxConnections || 1000,
            };

            if (editingBroker) {
                await mqttBrokerApi.updateBroker(editingBroker.id, brokerRequest);
                message.success('更新成功');
            } else {
                await mqttBrokerApi.addBroker(brokerRequest)
                message.success('添加成功');
            }
            setIsModalVisible(false);
            form.resetFields();
            await loadBrokers();
        } catch {
            message.error('操作失败');
        }
    };

    const handleModalCancel = () => {
        setIsModalVisible(false);
        form.resetFields();
    };

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
                <Title level={2}>MQTT Broker 管理</Title>
                <Space>
                    <Button
                        icon={<ReloadOutlined />}
                        onClick={loadBrokers}
                    >
                        刷新
                    </Button>
                    <Button
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={handleAdd}
                    >
                        添加 Broker
                    </Button>
                </Space>
            </div>

            <Card>
                <Spin spinning={loading}>
                    <Table
                        columns={columns}
                        dataSource={brokers}
                        rowKey="brokerId"
                        pagination={{
                            pageSize: 10,
                            showSizeChanger: true,
                            showQuickJumper: true,
                            showTotal: (total) => `共 ${total} 条记录`,
                        }}
                    />
                </Spin>
            </Card>

            {/* 添加/编辑 Broker 模态框 */}
            <Modal
                title={editingBroker ? '编辑 Broker' : '添加 Broker'}
                open={isModalVisible}
                onOk={handleModalOk}
                onCancel={handleModalCancel}
                width={800}
            >
                <Form
                    form={form}
                    layout="vertical"
                >
                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item
                                name="name"
                                label="Broker 名称"
                                rules={[{ required: true, message: '请输入 Broker 名称' }]}
                            >
                                <Input placeholder="请输入 Broker 名称" />
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                name="enabled"
                                label="是否启用"
                                valuePropName="checked"
                                initialValue={true}
                            >
                                <Switch checkedChildren="启用" unCheckedChildren="禁用" />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item
                                name="host"
                                label="主机地址"
                                rules={[{ required: true, message: '请输入主机地址' }]}
                            >
                                <Input placeholder="例如: 127.0.0.1 或 broker.example.com" />
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                name="port"
                                label="端口"
                                rules={[{ required: true, message: '请输入端口号' }]}
                            >
                                <InputNumber
                                    placeholder="1883"
                                    min={1}
                                    max={65535}
                                    style={{ width: '100%' }}
                                />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Form.Item
                        name="description"
                        label="备注"
                    >
                        <Input.TextArea placeholder="可选，输入 Broker 的描述信息" rows={2} />
                    </Form.Item>

                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item
                                name="maxConnections"
                                label="最大连接数"
                                initialValue={1000}
                            >
                                <InputNumber
                                    placeholder="1000"
                                    min={1}
                                    style={{ width: '100%' }}
                                />
                            </Form.Item>
                        </Col>
                    </Row>
                </Form>
            </Modal>

            {/* Broker 详情模态框 */}
            <Modal
                title={`Broker 详情 - ${selectedBrokerId}`}
                open={detailModalVisible}
                onCancel={() => setDetailModalVisible(false)}
                footer={null}
                width={800}
            >
                {brokerDetail && (
                    <Descriptions bordered column={2}>
                        <Descriptions.Item label="Broker ID">{brokerDetail.brokerId}</Descriptions.Item>
                        <Descriptions.Item label="名称">{brokerDetail.name}</Descriptions.Item>
                        <Descriptions.Item label="主机地址">{brokerDetail.host}</Descriptions.Item>
                        <Descriptions.Item label="端口">{brokerDetail.port}</Descriptions.Item>
                        <Descriptions.Item label="是否启用">
                            <Tag color={brokerDetail.enabled ? 'green' : 'red'}>
                                {brokerDetail.enabled ? '启用' : '禁用'}
                            </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="备注" span={2}>
                            {brokerDetail.description || '-'}
                        </Descriptions.Item>
                        <Descriptions.Item label="创建时间">
                            {brokerDetail.createdAt ? dayjs(brokerDetail.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
                        </Descriptions.Item>
                        <Descriptions.Item label="更新时间">
                            {brokerDetail.updatedAt ? dayjs(brokerDetail.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
                        </Descriptions.Item>
                    </Descriptions>
                )}
            </Modal>

        </div>
    );
};

export default MqttBrokerManagement;