import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Table, Button, Space, Input, Modal, Form, Select, Tag, Popconfirm, Spin, Typography, InputNumber, message, Descriptions } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined, ReloadOutlined, SettingOutlined } from '@ant-design/icons';
import mqttBrokerApi from '../services/mqttBrokerApi';
import groupApi from '../services/groupApi';
import type { BrokerListItem, MqttBrokerConfig } from '../types/mqttBroker';
import type { MqttGroup } from '../types/mqttGroup';
import dayjs from 'dayjs';

const { Title } = Typography;

const MqttBrokerManagement: React.FC = () => {
    const navigate = useNavigate();
    const [brokers, setBrokers] = useState<BrokerListItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [isModalVisible, setIsModalVisible] = useState(false);
    const [editingBroker, setEditingBroker] = useState<BrokerListItem | null>(null);
    const [form] = Form.useForm();
    const [selectedGroup, setSelectedGroup] = useState<string>('');
    const [groupSelectOptions, setGroupSelectOptions] = useState<{ label: string; value: string }[]>([]);
    const [defaultGroupId, setDefaultGroupId] = useState<string>('');
    const [detailVisible, setDetailVisible] = useState(false);
    const [brokerDetail, setBrokerDetail] = useState<MqttBrokerConfig | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);

    // 加载 Broker 列表
    const loadBrokers = async (group?: string) => {
        setLoading(true);
        try {
            const pageInfo = await mqttBrokerApi.getAllBrokers(undefined, group ?? selectedGroup);
            setBrokers(pageInfo.content || []);
        } catch (error) {
            console.error('加载 Broker 列表失败:', error);
        } finally {
            setLoading(false);
        }
    };

    // 加载分组选项（用于下拉选择）
    const loadGroupSelectOptions = async () => {
        try {
            // 先确保默认分组存在
            const defaultGroup = await groupApi.getOrCreateDefaultGroup();
            setDefaultGroupId(defaultGroup.id);
            setSelectedGroup(defaultGroup.id);

            // 获取所有分组
            const allGroups = await groupApi.getAllGroupsForSelect();

            // 确保"默认分组"在列表第一位
            const otherGroups = allGroups.filter((g: MqttGroup) => g.name !== '默认分组');
            const sortedGroups = [defaultGroup, ...otherGroups];

            const options = sortedGroups.map((g: MqttGroup) => ({
                label: g.name,
                value: g.id
            }));
            setGroupSelectOptions(options);
            loadBrokers(defaultGroup.id);
        } catch (error) {
            console.error('加载分组选项失败:', error);
        }
    };

    // 初始化加载
    useEffect(() => {
        loadGroupSelectOptions();
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
            title: '分组',
            dataIndex: 'group',
            key: 'group',
            width: 120,
            render: (group: string) => {
                const groupName = groupSelectOptions.find(opt => opt.value === group)?.label;
                return group ? <Tag color="blue">{groupName || group}</Tag> : <span>-</span>;
            },
        },
        {
            title: '主机地址',
            dataIndex: 'host',
            key: 'host',
            width: 120,
            ellipsis: true,
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
                        onClick={() => handleViewDetail(record.id, record.brokerId)}
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
        // 设置默认分组
        if (defaultGroupId) {
            form.setFieldValue('group', defaultGroupId);
        }
        setIsModalVisible(true);
    };

    const handleEdit = (broker: BrokerListItem) => {
        setEditingBroker(broker);
        form.setFieldsValue({
            name: broker.name,
            host: broker.host,
            port: broker.port,
            description: broker.description || '',
            group: broker.group || '',
        });
        setIsModalVisible(true);
    };

    const handleDelete = async (id: string) => {
        try {
            await mqttBrokerApi.deleteBroker(id);
            message.success('删除成功');
            loadBrokers();
            loadGroupSelectOptions();
        } catch (error) {
            message.error('删除失败');
        }
    };

    const handleToggleStatus = async (id: string, enabled: boolean) => {
        try {
            await mqttBrokerApi.toggleBrokerStatus(id, enabled);
            message.success(enabled ? '已启用' : '已禁用');
            loadBrokers();
        } catch (error) {
            message.error('操作失败');
            loadBrokers(); // 重新加载以恢复正确状态
        }
    };

    const handleViewDetail = async (id: string, _brokerId: string) => {
        setDetailLoading(true);
        setDetailVisible(true);
        try {
            const detail = await mqttBrokerApi.getBrokerDetails(id);
            setBrokerDetail(detail);
        } catch (error) {
            message.error('加载 Broker 详情失败');
            console.error('Failed to load broker detail:', error);
        } finally {
            setDetailLoading(false);
        }
    };

    const handleModalOk = async () => {
        try {
            const values = await form.validateFields();
            const brokerRequest: any = {
                id: editingBroker?.id || '',
                name: values.name,
                host: values.host,
                port: values.port,
                description: values.description || '',
                group: values.group || '',
            };

            if (editingBroker) {
                await mqttBrokerApi.updateBroker(editingBroker.id, brokerRequest);
                message.success('更新成功');
            } else {
                await mqttBrokerApi.addBroker(brokerRequest);
                message.success('添加成功');
            }

            setIsModalVisible(false);
            setEditingBroker(null);
            form.resetFields();
            loadBrokers();
            loadGroupSelectOptions();
        } catch (error) {
            message.error('操作失败');
        }
    };

    const handleGroupChange = (group: string) => {
        setSelectedGroup(group);
        loadBrokers(group);
    };

    const handleRefresh = () => {
        loadBrokers();
        loadGroupSelectOptions();
    };

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
                <Title level={2}>MQTT Broker 管理</Title>
                <Space>
                    <Select
                        placeholder="选择分组"
                        style={{ width: 150 }}
                        value={selectedGroup}
                        onChange={handleGroupChange}
                        options={groupSelectOptions}
                    />
                    <Button icon={<ReloadOutlined />} onClick={handleRefresh}>
                        刷新
                    </Button>
                    <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
                        添加 Broker
                    </Button>
                </Space>
            </div>

            <Card>
                <Spin spinning={loading}>
                    <Table
                        columns={columns}
                        dataSource={brokers}
                        rowKey="id"
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
                onCancel={() => {
                    setIsModalVisible(false);
                    setEditingBroker(null);
                }}
                width={500}
            >
                <Form form={form} layout="vertical">
                    <Form.Item
                        name="name"
                        label="Broker 名称"
                        rules={[{ required: true, message: '请输入 Broker 名称' }]}
                    >
                        <Input placeholder="请输入 Broker 名称" />
                    </Form.Item>

                    <Form.Item
                        name="host"
                        label="主机地址"
                        rules={[{ required: true, message: '请输入主机地址' }]}
                    >
                        <Input placeholder="例如: 127.0.0.1" />
                    </Form.Item>

                    <Form.Item
                        name="port"
                        label="端口"
                        rules={[{ required: true, message: '请输入端口号' }]}
                    >
                        <InputNumber min={1} max={65535} style={{ width: '100%' }} />
                    </Form.Item>

                    <Form.Item
                        name="group"
                        label="分组/项目"
                    >
                        <Select
                            placeholder="请选择分组"
                            options={groupSelectOptions}
                            dropdownRender={(menu) => (
                                <>
                                    {menu}
                                    <Button
                                        type="link"
                                        icon={<SettingOutlined />}
                                        style={{ fontSize: 12, marginLeft: 8 }}
                                        onClick={() => navigate('/mqtt-instances?tab=groups')}
                                    >
                                        管理分组
                                    </Button>
                                </>
                            )}
                        />
                    </Form.Item>

                    <Form.Item
                        name="description"
                        label="备注"
                    >
                        <Input.TextArea placeholder="可选，输入 Broker 的描述信息" rows={2} />
                    </Form.Item>
                </Form>
            </Modal>

            {/* Broker 详情模态框 */}
            <Modal
                title="Broker 详情"
                open={detailVisible}
                onCancel={() => {
                    setDetailVisible(false);
                    setBrokerDetail(null);
                }}
                footer={[
                    <Button key="close" onClick={() => {
                        setDetailVisible(false);
                        setBrokerDetail(null);
                    }}>
                        关闭
                    </Button>
                ]}
                width={600}
            >
                <Spin spinning={detailLoading}>
                    {brokerDetail && (
                        <Descriptions column={2} bordered size="small">
                            <Descriptions.Item label="名称" span={2}>{brokerDetail.name}</Descriptions.Item>
                            <Descriptions.Item label="分组" span={2}>
                                {brokerDetail.group ? (
                                    <Tag color="blue">{groupSelectOptions.find(opt => opt.value === brokerDetail.group)?.label || brokerDetail.group}</Tag>
                                ) : '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="主机地址">{brokerDetail.host}</Descriptions.Item>
                            <Descriptions.Item label="端口">
                                <Tag color="blue">{brokerDetail.port}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="是否启用">
                                <Tag color={brokerDetail.enabled ? 'green' : 'red'}>
                                    {brokerDetail.enabled ? '启用' : '禁用'}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="备注" span={2}>{brokerDetail.description || '-'}</Descriptions.Item>
                            <Descriptions.Item label="创建时间">
                                {brokerDetail.createdAt
                                    ? dayjs(brokerDetail.createdAt).format('YYYY-MM-DD HH:mm:ss')
                                    : '-'}
                            </Descriptions.Item>
                        </Descriptions>
                    )}
                </Spin>
            </Modal>
        </div>
    );
};

export default MqttBrokerManagement;
