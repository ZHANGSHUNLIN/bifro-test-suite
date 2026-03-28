import React, { useState, useEffect } from 'react';
import { Card, Table, Button, Space, Input, Modal, Form, Popconfirm, Spin, Typography, message, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import groupApi from '../services/groupApi';
import type { GroupListItem } from '../types/mqttGroup';
import dayjs from 'dayjs';

const { Title } = Typography;

const MqttGroupManagement: React.FC = () => {
    const [groups, setGroups] = useState<GroupListItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [isModalVisible, setIsModalVisible] = useState(false);
    const [editingGroup, setEditingGroup] = useState<GroupListItem | null>(null);
    const [form] = Form.useForm();

    // 加载分组列表
    const loadGroups = async () => {
        setLoading(true);
        try {
            const pageInfo = await groupApi.getAllGroups();
            setGroups(pageInfo.content || []);
        } catch (error) {
            message.error('加载分组列表失败');
            console.error('Failed to load groups:', error);
        } finally {
            setLoading(false);
        }
    };

    // 初始化加载
    useEffect(() => {
        loadGroups();
    }, []);

    const columns = [
        {
            title: '名称',
            dataIndex: 'name',
            key: 'name',
            width: 150,
        },
        {
            title: '描述',
            dataIndex: 'description',
            key: 'description',
            width: 300,
            ellipsis: true,
        },
        {
            title: 'Broker 数量',
            dataIndex: 'brokerCount',
            key: 'brokerCount',
            width: 120,
            render: (count: number) => <Tag color="blue">{count}</Tag>,
        },
        {
            title: '创建时间',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 180,
            render: (createdAt: string) => createdAt ? dayjs(createdAt).format('YYYY-MM-DD HH:mm:ss') : '-',
        },
        {
            title: '操作',
            key: 'action',
            width: 180,
            render: (_: unknown, record: GroupListItem) => (
                <Space.Compact size="small">
                    <Button
                        type="link"
                        icon={<EditOutlined />}
                        onClick={() => handleEdit(record)}
                    >
                        编辑
                    </Button>
                    <Popconfirm
                        title="确定要删除这个分组吗？"
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
        setEditingGroup(null);
        form.resetFields();
        setIsModalVisible(true);
    };

    const handleEdit = (group: GroupListItem) => {
        setEditingGroup(group);
        form.setFieldsValue({
            name: group.name,
            description: group.description || '',
        });
        setIsModalVisible(true);
    };

    const handleDelete = async (id: string) => {
        try {
            await groupApi.deleteGroup(id);
            message.success('删除成功');
            loadGroups();
        } catch (error) {
            // 错误信息已经由后端 API 处理
            const errorMessage = error instanceof Error ? error.message : '删除失败';
            message.error(errorMessage);
        }
    };

    const handleModalOk = async () => {
        try {
            const values = await form.validateFields();
            const request = {
                name: values.name,
                description: values.description || '',
            };

            if (editingGroup) {
                await groupApi.updateGroup(editingGroup.id, request);
                message.success('更新成功');
            } else {
                await groupApi.addGroup(request);
                message.success('添加成功');
            }

            setIsModalVisible(false);
            setEditingGroup(null);
            form.resetFields();
            loadGroups();
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : '操作失败';
            message.error(errorMessage);
        }
    };

    const handleRefresh = () => {
        loadGroups();
    };

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
                <Title level={2}>MQTT Broker 分组管理</Title>
                <Space>
                    <Button icon={<ReloadOutlined />} onClick={handleRefresh}>
                        刷新
                    </Button>
                    <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
                        添加分组
                    </Button>
                </Space>
            </div>

            <Card>
                <Spin spinning={loading}>
                    <Table
                        columns={columns}
                        dataSource={groups}
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

            {/* 添加/编辑分组模态框 */}
            <Modal
                title={editingGroup ? '编辑分组' : '添加分组'}
                open={isModalVisible}
                onOk={handleModalOk}
                onCancel={() => {
                    setIsModalVisible(false);
                    setEditingGroup(null);
                }}
                width={500}
            >
                <Form form={form} layout="vertical">
                    <Form.Item
                        name="name"
                        label="分组名称"
                        rules={[
                            { required: true, message: '请输入分组名称' },
                            { max: 50, message: '分组名称不能超过50个字符' }
                        ]}
                    >
                        <Input placeholder="请输入分组名称" maxLength={50} />
                    </Form.Item>
                    <Form.Item
                        name="description"
                        label="分组描述"
                        rules={[
                            { max: 200, message: '分组描述不能超过200个字符' }
                        ]}
                    >
                        <Input.TextArea placeholder="可选，输入分组的描述信息" rows={3} maxLength={200} />
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default MqttGroupManagement;
