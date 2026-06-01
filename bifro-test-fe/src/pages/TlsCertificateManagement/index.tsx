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
import {
    Button,
    Card,
    Form,
    Input,
    message,
    Modal,
    Popconfirm,
    Space,
    Spin,
    Table,
    Tabs,
    Tag,
    Upload
} from 'antd';
import {DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SearchOutlined, UploadOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import {certificateApi} from '../../features/certificate';
import type {CertType, TlsCertificate, TlsCertificateCreateReq} from '../../features/certificate';
import dayjs from 'dayjs';
import {useTablePagination} from '../../hooks/useTablePagination';

const {TextArea} = Input;

const TlsCertificateManagement: React.FC = () => {
    const {t} = useTranslation();
    const [certificates, setCertificates] = useState<TlsCertificate[]>([]);
    const [loading, setLoading] = useState(false);
    const [isModalVisible, setIsModalVisible] = useState(false);
    const [editingCertificate, setEditingCertificate] = useState<TlsCertificate | null>(null);
    const [activeTab, setActiveTab] = useState<CertType>('CA');
    const [form] = Form.useForm();
    const [certFile, setCertFile] = useState<File | null>(null);
    const [keyFile, setKeyFile] = useState<File | null>(null);
    const [keyword, setKeyword] = useState('');
    const {
        currentPage,
        pageSize,
        applyPageInfo,
        getTablePagination,
    } = useTablePagination({defaultPageSize: 10, totalLabel: t('common.total')});

    // Load certificate list
    const loadCertificates = async (
        type: CertType = activeTab,
        page: number = currentPage,
        size: number = pageSize,
        searchKeyword: string = keyword
    ) => {
        setLoading(true);
        try {
            const pageInfo = await certificateApi.getCertificates(type, page, size, searchKeyword);
            setCertificates(pageInfo.content || []);
            applyPageInfo(pageInfo, page, size);
        } catch (error) {
            message.error(t('certificate.msg.loadFailed'));
            console.error('Failed to load certificates:', error);
        } finally {
            setLoading(false);
        }
    };

    // Initial load
    useEffect(() => {
        loadCertificates();
    }, []);

    // Reload on Tab switch
    const handleTabChange = (key: string) => {
        setActiveTab(key as CertType);
        loadCertificates(key as CertType, 1, pageSize, keyword);
    };

    const handleSearch = () => {
        loadCertificates(activeTab, 1, pageSize, keyword);
    };

    const handleClearFilters = () => {
        setKeyword('');
        loadCertificates(activeTab, 1, pageSize, '');
    };

    // Read file content
    const readFileAsText = (file: File): Promise<string> => {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result as string);
            reader.onerror = reject;
            reader.readAsText(file);
        });
    };

    // Get certificate status tag
    const getStatusTag = (validTo: string) => {
        const now = dayjs();
        const validToDate = dayjs(validTo);
        const daysUntilExpiry = validToDate.diff(now, 'day');

        if (daysUntilExpiry < 0) {
            return <Tag color="red">{t('certificate.status.expired')}</Tag>;
        } else if (daysUntilExpiry <= 30) {
            return <Tag color="orange">{t('certificate.status.expiringSoon', {days: daysUntilExpiry})}</Tag>;
        } else {
            return <Tag color="green">{t('certificate.status.normal')}</Tag>;
        }
    };

    const columns = [
        {
            title: t('certificate.columns.name'),
            dataIndex: 'name',
            key: 'name',
            width: 150,
        },
        {
            title: t('certificate.columns.type'),
            dataIndex: 'type',
            key: 'type',
            width: 100,
            render: (type: CertType) => (
                <Tag color={type === 'CA' ? 'blue' : 'purple'}>
                    {type === 'CA' ? t('certificate.typeLabel.CA') : t('certificate.typeLabel.CLIENT')}
                </Tag>
            ),
        },
        {
            title: t('certificate.columns.subject'),
            dataIndex: 'subjectDN',
            key: 'subjectDN',
            width: 300,
            ellipsis: true,
        },
        {
            title: t('certificate.columns.expireAt'),
            dataIndex: 'validTo',
            key: 'validTo',
            width: 180,
            render: (validTo: string) => dayjs(validTo).format('YYYY-MM-DD HH:mm:ss'),
        },
        {
            title: t('certificate.status.normal'),
            dataIndex: 'validTo',
            key: 'status',
            width: 120,
            render: (validTo: string) => getStatusTag(validTo),
        },
        {
            title: t('common.createdAt'),
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 180,
            render: (createdAt: string) => createdAt ? dayjs(createdAt).format('YYYY-MM-DD HH:mm:ss') : '-',
        },
        {
            title: t('common.actions'),
            key: 'action',
            width: 180,
            render: (_: unknown, record: TlsCertificate) => (
                <Space.Compact size="small">
                    <Button
                        type="link"
                        icon={<EditOutlined/>}
                        onClick={() => handleEdit(record)}
                    >
                        {t('common.edit')}
                    </Button>
                    <Popconfirm
                        title={t('certificate.deleteConfirm')}
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
        setEditingCertificate(null);
        setCertFile(null);
        setKeyFile(null);
        form.resetFields();
        setIsModalVisible(true);
    };

    const handleEdit = (cert: TlsCertificate) => {
        setEditingCertificate(cert);
        setCertFile(null);
        setKeyFile(null);
        form.setFieldsValue({
            name: cert.name,
        });
        setIsModalVisible(true);
    };

    const handleDelete = async (id: string) => {
        try {
            await certificateApi.deleteCertificate(id);
            message.success(t('certificate.msg.deleteSuccess'));
            loadCertificates(activeTab, currentPage, pageSize, keyword);
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : t('common.operationFailed');
            message.error(errorMessage);
        }
    };

    const handleCertFileChange = (info: any) => {
        const file = info.fileList[0]?.originFileObj;
        if (file) {
            setCertFile(file);
        }
    };

    const handleKeyFileChange = (info: any) => {
        const file = info.fileList[0]?.originFileObj;
        if (file) {
            setKeyFile(file);
        }
    };

    const handleModalOk = async () => {
        try {
            const values = await form.validateFields();

            // Validate required fields
            if (!certFile && !editingCertificate && !values.certContent) {
                message.error(t('certificate.certName'));
                return;
            }

            // Client certificate must provide private key
            if (activeTab === 'CLIENT' && !keyFile && !editingCertificate && !values.keyContent) {
                message.error(t('certificate.typeLabel.CLIENT'));
                return;
            }

            let certContent = values.certContent;
            let keyContent: string | undefined = values.keyContent;

            if (certFile) {
                certContent = await readFileAsText(certFile);
            }

            if (keyFile) {
                keyContent = await readFileAsText(keyFile);
            }

            const request: TlsCertificateCreateReq = {
                name: values.name,
                type: activeTab,
                certContent,
                keyContent: activeTab === 'CLIENT' ? keyContent : undefined,
            };

            if (editingCertificate) {
                await certificateApi.updateCertificate(editingCertificate.id, {name: values.name});
                message.success(t('certificate.msg.uploadSuccess'));
            } else {
                await certificateApi.createCertificate(request);
                message.success(t('certificate.msg.uploadSuccess'));
            }

            setIsModalVisible(false);
            setEditingCertificate(null);
            setCertFile(null);
            setKeyFile(null);
            form.resetFields();
            loadCertificates(activeTab, editingCertificate ? currentPage : 1, pageSize, keyword);
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : t('common.operationFailed');
            message.error(errorMessage);
        }
    };

    const handleRefresh = () => {
        loadCertificates(activeTab, currentPage, pageSize, keyword);
    };

    return (
        <div>
            <Card>
                <div style={{display: 'flex', justifyContent: 'flex-end', marginBottom: 16, gap: 12}}>
                    <div style={{display: 'flex', gap: 12, flex: 1}}>
                        <Input
                            placeholder={t('common.search') + t('certificate.certName')}
                            value={keyword}
                            onChange={(e) => setKeyword(e.target.value)}
                            onPressEnter={handleSearch}
                            style={{width: 220}}
                            allowClear
                            prefix={<SearchOutlined style={{color: '#bfbfbf'}}/>}
                            onClear={handleClearFilters}
                        />
                        <Button type="primary" icon={<SearchOutlined/>} onClick={handleSearch}>
                            {t('common.search')}
                        </Button>
                        {keyword && (
                            <Button onClick={handleClearFilters}>{t('common.reset')}</Button>
                        )}
                    </div>
                    <Button icon={<ReloadOutlined/>} onClick={handleRefresh}>
                        {t('common.refresh')}
                    </Button>
                    <Button type="primary" icon={<PlusOutlined/>} onClick={handleAdd}>
                        {t('certificate.createCert')}
                    </Button>
                </div>
                <Tabs
                    activeKey={activeTab}
                    onChange={handleTabChange}
                    items={[
                        {
                            key: 'CA',
                            label: t('certificate.typeLabel.CA'),
                        },
                        {
                            key: 'CLIENT',
                            label: t('certificate.typeLabel.CLIENT'),
                        },
                    ]}
                />
                <Spin spinning={loading}>
                    <Table
                        columns={columns}
                        dataSource={certificates}
                        rowKey="id"
                        pagination={getTablePagination((page, size) =>
                            loadCertificates(activeTab, page, size, keyword))}
                    />
                </Spin>
            </Card>

            {/* Add/Edit certificate modal */}
            <Modal
                title={editingCertificate ? t('common.edit') + t('certificate.title') : t('certificate.createCert') + (activeTab === 'CA' ? t('certificate.typeLabel.CA') : t('certificate.typeLabel.CLIENT'))}
                open={isModalVisible}
                onOk={handleModalOk}
                onCancel={() => {
                    setIsModalVisible(false);
                    setEditingCertificate(null);
                    setCertFile(null);
                    setKeyFile(null);
                    form.resetFields();
                }}
                width={600}
            >
                <Form form={form} layout="vertical">
                    <Form.Item
                        name="name"
                        label={t('certificate.certName')}
                        rules={[
                            {required: true, message: t('certificate.certName')},
                            {max: 100, message: t('certificate.certName')}
                        ]}
                    >
                        <Input placeholder={t('certificate.certName')} maxLength={100}/>
                    </Form.Item>

                    <Form.Item
                        label={t('common.upload')}
                        extra={t('certificate.extra.certFormat')}
                    >
                        <Upload
                            beforeUpload={() => false}
                            onChange={handleCertFileChange}
                            fileList={certFile ? [certFile as any] : []}
                            maxCount={1}
                            accept=".crt,.pem"
                        >
                            <Button icon={<UploadOutlined/>}>{t('common.upload')}</Button>
                        </Upload>
                    </Form.Item>

                    {activeTab === 'CLIENT' && (
                        <Form.Item
                            label={t('task.form.caCert')}
                            extra={t('certificate.extra.keyFormat')}
                        >
                            <Upload
                                beforeUpload={() => false}
                                onChange={handleKeyFileChange}
                                fileList={keyFile ? [keyFile as any] : []}
                                maxCount={1}
                                accept=".key"
                            >
                                <Button icon={<UploadOutlined/>}>{t('common.upload')}</Button>
                            </Upload>
                        </Form.Item>
                    )}

                    <Form.Item
                        name="certContent"
                        label={t('certificate.certName')}
                        rules={[{required: !certFile && !editingCertificate, message: t('certificate.certName')}]}
                    >
                        <TextArea
                            placeholder="-----BEGIN CERTIFICATE-----&#10;...&#10;-----END CERTIFICATE-----"
                            rows={8}
                            disabled={!!certFile || !!editingCertificate}
                        />
                    </Form.Item>

                    {activeTab === 'CLIENT' && (
                        <Form.Item
                            name="keyContent"
                            label={t('task.form.caCert')}
                            rules={[{
                                required: activeTab === 'CLIENT' && !keyFile && !editingCertificate,
                                message: t('task.form.caCert')
                            }]}
                        >
                            <TextArea
                                placeholder="-----BEGIN PRIVATE KEY-----&#10;...&#10;-----END PRIVATE KEY-----"
                                rows={8}
                                disabled={!!keyFile || !!editingCertificate}
                            />
                        </Form.Item>
                    )}
                </Form>
            </Modal>
        </div>
    );
};

export default TlsCertificateManagement;
