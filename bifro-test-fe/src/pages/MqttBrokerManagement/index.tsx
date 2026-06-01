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
import {useNavigate} from 'react-router-dom';
import {
    Button,
    Card,
    Descriptions,
    Form,
    Input,
    InputNumber,
    message,
    Modal,
    Popconfirm,
    Select,
    Space,
    Spin,
    Table,
    Tag
} from 'antd';
import {DeleteOutlined, EditOutlined, EyeOutlined, PlusOutlined, SettingOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import {brokerApi as mqttBrokerApi} from '../../features/broker';
import groupApi from '../../features/group';
import {certificateApi} from '../../features/certificate';
import type {BrokerListItem, MqttBrokerConfig} from '../../features/broker';
import type {MqttGroup} from '../../features/group';
import type {TlsCertificate} from '../../features/certificate';
import dayjs from 'dayjs';
import {useTablePagination} from '../../hooks/useTablePagination';

const MqttBrokerManagement: React.FC = () => {
    const navigate = useNavigate();
    const {t} = useTranslation();
    const [brokers, setBrokers] = useState<BrokerListItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [isModalVisible, setIsModalVisible] = useState(false);
    const [editingBroker, setEditingBroker] = useState<BrokerListItem | null>(null);
    const [form] = Form.useForm();
    const [selectedGroup, setSelectedGroup] = useState<string>('');
    const [groupSelectOptions, setGroupSelectOptions] = useState<{ label: string; value: string }[]>([]);
    const [detailVisible, setDetailVisible] = useState(false);
    const [brokerDetail, setBrokerDetail] = useState<MqttBrokerConfig | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [certSelectOptions, setCertSelectOptions] = useState<{ label: string; value: string }[]>([]);
    const {
        currentPage,
        pageSize,
        applyPageInfo,
        getTablePagination,
    } = useTablePagination({defaultPageSize: 10, totalLabel: t('common.total')});

    // Load Broker list
    const loadBrokers = async (group: string = selectedGroup, page: number = currentPage, size: number = pageSize) => {
        setLoading(true);
        try {
            const pageInfo = await mqttBrokerApi.getAllBrokers(undefined, group || undefined, page, size);
            setBrokers(pageInfo.content || []);
            applyPageInfo(pageInfo, page, size);
        } catch (error) {
            console.error('Failed to load Broker list:', error);
        } finally {
            setLoading(false);
        }
    };

    // Load group options (for dropdown)
    const loadGroupSelectOptions = async () => {
        try {
            const allGroups = await groupApi.getAllGroupsForSelect('BROKER');
            const options = allGroups.map((g: MqttGroup) => ({
                label: g.name,
                value: g.id
            }));
            setGroupSelectOptions(options);

            const nextGroup = selectedGroup && options.some((option) => option.value === selectedGroup)
                ? selectedGroup
                : options[0]?.value || '';
            setSelectedGroup(nextGroup);
            loadBrokers(nextGroup, 1, pageSize);
        } catch (error) {
            console.error('Failed to load group options:', error);
        }
    };

    // Load certificate options (for dropdown)
    const loadCertSelectOptions = async () => {
        try {
            const allCerts = await certificateApi.getAllCertificates('CA');
            const options = allCerts.map((cert: TlsCertificate) => ({
                label: cert.name,
                value: cert.id
            }));
            setCertSelectOptions(options);
        } catch (error) {
            console.error('Failed to load certificate options:', error);
        }
    };

    // Load certificate options and pre-fill when editing
    const loadCertSelectOptionsWithEdit = async (broker: BrokerListItem | null) => {
        try {
            const allCerts = await certificateApi.getAllCertificates('CA');
            const options = allCerts.map((cert: TlsCertificate) => ({
                label: cert.name,
                value: cert.id
            }));
            setCertSelectOptions(options);
            // If editing Broker with caCertId, pre-fill after certificate options loaded
            if (broker && broker.caCertId) {
                form.setFieldValue('caCertId', broker.caCertId);
            }
        } catch (error) {
            console.error('Failed to load certificate options:', error);
        }
    };

    // Initial load
    useEffect(() => {
        loadGroupSelectOptions();
        loadCertSelectOptions();
    }, []);

    const columns = [
        {
            title: t('broker.columns.name'),
            dataIndex: 'name',
            key: 'name',
            width: 120,
            ellipsis: true,
        },
        {
            title: t('broker.columns.group'),
            dataIndex: 'group',
            key: 'group',
            width: 120,
            render: (group: string) => {
                const groupName = groupSelectOptions.find(opt => opt.value === group)?.label;
                return group ? <Tag color="blue">{groupName || group}</Tag> : <span>-</span>;
            },
        },
        {
            title: t('broker.columns.caCert'),
            dataIndex: 'caCertId',
            key: 'caCertId',
            width: 150,
            render: (certId: string) => {
                const certName = certSelectOptions.find(opt => opt.value === certId)?.label;
                return certName ? <Tag color="purple">{certName}</Tag> : <span>-</span>;
            },
        },
        {
            title: t('broker.columns.host'),
            dataIndex: 'host',
            key: 'host',
            width: 120,
            ellipsis: true,
        },
        {
            title: t('broker.columns.port'),
            dataIndex: 'port',
            key: 'port',
            width: 80,
            render: (port: number) => <Tag color="blue">{port}</Tag>,
        },
        {
            title: t('broker.columns.status'),
            dataIndex: 'enabled',
            key: 'enabled',
            width: 100,
            render: (enabled: boolean) => (
                <Tag color={enabled ? 'green' : 'red'}>
                    {enabled ? t('common.enable') : t('common.disable')}
                </Tag>
            ),
        },
        {
            title: t('common.description'),
            dataIndex: 'description',
            key: 'description',
            width: 150,
            ellipsis: true,
        },
        {
            title: t('broker.columns.actions'),
            key: 'action',
            width: 280,
            render: (_: unknown, record: BrokerListItem) => (
                <Space.Compact size="small">
                    <Button
                        type="link"
                        icon={<EyeOutlined/>}
                        onClick={() => handleViewDetail(record.id, record.brokerId)}
                    >
                        {t('common.detail')}
                    </Button>
                    <Button
                        type="link"
                        icon={<EditOutlined/>}
                        onClick={() => handleEdit(record)}
                    >
                        {t('common.edit')}
                    </Button>
                    <Button
                        type="link"
                        onClick={() => handleToggleStatus(record.id, !record.enabled)}
                    >
                        {record.enabled ? t('common.disable') : t('common.enable')}
                    </Button>
                    <Popconfirm
                        title={t('broker.deleteConfirm')}
                        onConfirm={() => handleDelete(record.id)}
                        okText={t('common.confirm')}
                        cancelText={t('common.cancel')}
                    >
                        <Button
                            type="link"
                            danger
                            icon={<DeleteOutlined/>}
                        >
                            {t('common.delete')}
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
            description: broker.description || '',
            group: broker.group || '',
            caCertId: broker.caCertId || '',
        });
        // When editing, load certificate options first to ensure dropdown shows correct values
        loadCertSelectOptionsWithEdit(broker);
        setIsModalVisible(true);
    };

    const handleDelete = async (id: string) => {
        try {
            await mqttBrokerApi.deleteBroker(id);
            message.success(t('broker.msg.deleteSuccess'));
            loadBrokers(selectedGroup, currentPage, pageSize);
            loadGroupSelectOptions();
        } catch (error) {
            message.error(t('common.operationFailed'));
        }
    };

    const handleToggleStatus = async (id: string, enabled: boolean) => {
        try {
            await mqttBrokerApi.toggleBrokerStatus(id, enabled);
            message.success(enabled ? t('broker.msg.enableSuccess') : t('broker.msg.disableSuccess'));
            loadBrokers(selectedGroup, currentPage, pageSize);
        } catch (error) {
            message.error(t('common.operationFailed'));
            loadBrokers(selectedGroup, currentPage, pageSize); // Reload to restore correct state
        }
    };

    const handleViewDetail = async (id: string, _brokerId: string) => {
        setDetailLoading(true);
        setDetailVisible(true);
        try {
            const detail = await mqttBrokerApi.getBrokerDetails(id);
            setBrokerDetail(detail);
        } catch (error) {
            message.error(t('broker.msg.loadFailed'));
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
                caCertId: values.caCertId || undefined,
            };

            if (editingBroker) {
                await mqttBrokerApi.updateBroker(editingBroker.id, brokerRequest);
                message.success(t('broker.msg.updateSuccess'));
            } else {
                await mqttBrokerApi.addBroker(brokerRequest);
                message.success(t('broker.msg.addSuccess'));
            }

            setIsModalVisible(false);
            setEditingBroker(null);
            form.resetFields();
            loadBrokers(selectedGroup, editingBroker ? currentPage : 1, pageSize);
            loadGroupSelectOptions();
        } catch (error) {
            message.error(t('common.operationFailed'));
        }
    };

    const handleGroupChange = (group: string) => {
        const nextGroup = group || '';
        setSelectedGroup(nextGroup);
        loadBrokers(nextGroup, 1, pageSize);
    };

    return (
        <div>
            <Card>
                <Spin spinning={loading}>
                    <div style={{display: 'flex', justifyContent: 'flex-end', marginBottom: 16, gap: 12}}>
                        <Select
                            placeholder={t('common.group')}
                            style={{width: 150}}
                            value={selectedGroup || undefined}
                            onChange={handleGroupChange}
                            options={groupSelectOptions}
                            allowClear
                        />
                        <Button type="primary" icon={<PlusOutlined/>} onClick={handleAdd}>
                            {t('broker.createBroker')}
                        </Button>
                    </div>
                    <Table
                        columns={columns}
                        dataSource={brokers}
                        rowKey="id"
                        pagination={getTablePagination((page, size) => loadBrokers(selectedGroup, page, size))}
                    />
                </Spin>
            </Card>

            {/* Add/Edit Broker modal */}
            <Modal
                title={editingBroker ? `${t('common.edit')} ${t('nav.broker')}` : t('broker.createBroker')}
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
                        label={t('broker.brokerName')}
                        rules={[{required: true, message: t('broker.brokerName')}]}
                    >
                        <Input placeholder={t('broker.brokerName')}/>
                    </Form.Item>

                    <Form.Item
                        name="host"
                        label={t('broker.brokerHost')}
                        rules={[{required: true, message: t('broker.brokerHost')}]}
                    >
                        <Input placeholder={t('broker.hostPlaceholder')}/>
                    </Form.Item>

                    <Form.Item
                        name="port"
                        label={t('broker.brokerPort')}
                        rules={[{required: true, message: t('broker.brokerPort')}]}
                    >
                        <InputNumber min={1} max={65535} style={{width: '100%'}}/>
                    </Form.Item>

                    <Form.Item
                        name="group"
                        label={t('broker.brokerGroup')}
                    >
                        <Select
                            placeholder={t('broker.brokerGroup')}
                            options={groupSelectOptions}
                            allowClear
                            dropdownRender={(menu) => (
                                <>
                                    {menu}
                                    <Button
                                        type="link"
                                        icon={<SettingOutlined/>}
                                        style={{fontSize: 12, marginLeft: 8}}
                                        onClick={() => navigate('/mqtt-instances?tab=groups')}
                                    >
                                        {t('group.mqttGroup')}
                                    </Button>
                                </>
                            )}
                        />
                    </Form.Item>

                    <Form.Item
                        name="caCertId"
                        label={t('broker.caCert')}
                        extra={
                            <a onClick={() => navigate('/certificates')} style={{fontSize: 12}}>
                                {t('certificate.title')}
                            </a>
                        }
                    >
                        <Select
                            placeholder={t('broker.caCert')}
                            options={certSelectOptions}
                            allowClear
                        />
                    </Form.Item>

                    <Form.Item
                        name="description"
                        label={t('common.description')}
                    >
                        <Input.TextArea placeholder={t('common.description')} rows={2}/>
                    </Form.Item>
                </Form>
            </Modal>

            {/* Broker details modal */}
            <Modal
                title={t('broker.title')}
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
                        {t('common.close')}
                    </Button>
                ]}
                width={600}
            >
                <Spin spinning={detailLoading}>
                    {brokerDetail && (
                        <Descriptions column={2} bordered size="small">
                            <Descriptions.Item label={t('broker.columns.name')} span={2}>{brokerDetail.name}</Descriptions.Item>
                            <Descriptions.Item label={t('broker.columns.group')} span={2}>
                                {brokerDetail.group ? (
                                    <Tag
                                        color="blue">{groupSelectOptions.find(opt => opt.value === brokerDetail.group)?.label || brokerDetail.group}</Tag>
                                ) : '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('broker.columns.host')}>{brokerDetail.host}</Descriptions.Item>
                            <Descriptions.Item label={t('broker.columns.port')}>
                                <Tag color="blue">{brokerDetail.port}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('broker.columns.status')}>
                                <Tag color={brokerDetail.enabled ? 'green' : 'red'}>
                                    {brokerDetail.enabled ? t('common.enable') : t('common.disable')}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('common.description')}
                                               span={2}>{brokerDetail.description || '-'}</Descriptions.Item>
                            <Descriptions.Item label={t('common.createdAt')}>
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
