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

import {useEffect, useState} from 'react';
import {Button, Card, Form, Input, message, Modal, Popconfirm, Space, Spin, Table, Tag} from 'antd';
import {DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import groupApi, {type GroupType} from '../../features/group';
import dayjs from 'dayjs';
import {useTablePagination} from '../../hooks/useTablePagination';

interface BaseGroupListItem {
    id: string;
    name: string;
    description?: string;
    createdAt?: string;
}

interface GroupManagementPageProps<T extends BaseGroupListItem> {
    groupType: GroupType;
    countField: keyof T & string;
    countColumnTitleKey: string;
    createTitleKey: string;
    loadErrorLogLabel: string;
}

const GroupManagementPage = <T extends BaseGroupListItem>({
    groupType,
    countField,
    countColumnTitleKey,
    createTitleKey,
    loadErrorLogLabel,
}: GroupManagementPageProps<T>) => {
    const {t} = useTranslation();
    const [groups, setGroups] = useState<T[]>([]);
    const [loading, setLoading] = useState(false);
    const [isModalVisible, setIsModalVisible] = useState(false);
    const [editingGroup, setEditingGroup] = useState<T | null>(null);
    const [form] = Form.useForm();
    const [nameFilter, setNameFilter] = useState<string>('');
    const {
        currentPage,
        pageSize,
        applyPageInfo,
        getTablePagination,
    } = useTablePagination({defaultPageSize: 10, totalLabel: t('common.total')});

    const loadGroups = async (name: string = nameFilter, page: number = currentPage, size: number = pageSize) => {
        setLoading(true);
        try {
            const pageInfo = await groupApi.getAllGroups<T>(groupType, page, size, name);
            setGroups(pageInfo.content || []);
            applyPageInfo(pageInfo, page, size);
        } catch (error) {
            message.error(t('group.msg.loadFailed'));
            console.error(`Failed to load ${loadErrorLogLabel} groups:`, error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadGroups('');
    }, []);

    const handleSearch = () => {
        loadGroups(nameFilter, 1, pageSize);
    };

    const handleClearFilters = () => {
        setNameFilter('');
        loadGroups('', 1, pageSize);
    };

    const handleAdd = () => {
        setEditingGroup(null);
        form.resetFields();
        setIsModalVisible(true);
    };

    const handleEdit = (group: T) => {
        setEditingGroup(group);
        form.setFieldsValue({
            name: group.name,
            description: group.description || '',
        });
        setIsModalVisible(true);
    };

    const handleDelete = async (id: string) => {
        try {
            await groupApi.deleteGroup(groupType, id);
            message.success(t('group.msg.deleteSuccess'));
            loadGroups(nameFilter, currentPage, pageSize);
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : t('common.operationFailed');
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
                message.success(t('group.msg.updateSuccess'));
            } else {
                await groupApi.addGroup(groupType, request);
                message.success(t('group.msg.addSuccess'));
            }

            setIsModalVisible(false);
            setEditingGroup(null);
            form.resetFields();
            loadGroups(nameFilter, editingGroup ? currentPage : 1, pageSize);
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : t('common.operationFailed');
            message.error(errorMessage);
        }
    };

    const columns = [
        {
            title: t('group.columns.name'),
            dataIndex: 'name',
            key: 'name',
            width: 150,
        },
        {
            title: t('group.columns.description'),
            dataIndex: 'description',
            key: 'description',
            width: 300,
            ellipsis: true,
        },
        {
            title: t(countColumnTitleKey),
            dataIndex: countField,
            key: countField,
            width: 120,
            render: (count: number) => <Tag color="blue">{count ?? 0}</Tag>,
        },
        {
            title: t('group.columns.createdAt'),
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 180,
            render: (createdAt: string) => createdAt ? dayjs(createdAt).format('YYYY-MM-DD HH:mm:ss') : '-',
        },
        {
            title: t('group.columns.actions'),
            key: 'action',
            width: 180,
            render: (_: unknown, record: T) => (
                <Space.Compact size="small">
                    <Button
                        type="link"
                        icon={<EditOutlined/>}
                        onClick={() => handleEdit(record)}
                    >
                        {t('common.edit')}
                    </Button>
                    <Popconfirm
                        title={t('group.deleteConfirm')}
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

    return (
        <div>
            <Card>
                <div style={{display: 'flex', justifyContent: 'flex-end', marginBottom: 16, gap: 12}}>
                    <div style={{display: 'flex', gap: 12, flex: 1}}>
                        <Input
                            placeholder={t('common.search') + t('group.columns.name')}
                            value={nameFilter}
                            onChange={(e) => setNameFilter(e.target.value)}
                            onPressEnter={handleSearch}
                            style={{width: 200}}
                            allowClear
                            prefix={<SearchOutlined style={{color: '#bfbfbf'}}/>}
                        />
                        <Button type="primary" icon={<SearchOutlined/>} onClick={handleSearch}>{t('common.search')}</Button>
                        {nameFilter && (
                            <Button onClick={handleClearFilters}>{t('common.reset')}</Button>
                        )}
                    </div>
                    <Button type="primary" icon={<PlusOutlined/>} onClick={handleAdd}>
                        {t(createTitleKey)}
                    </Button>
                </div>
                <Spin spinning={loading}>
                    <Table
                        columns={columns}
                        dataSource={groups}
                        rowKey="id"
                        pagination={getTablePagination((page, size) => loadGroups(nameFilter, page, size))}
                    />
                </Spin>
            </Card>

            <Modal
                title={editingGroup ? t('group.editGroup') : t(createTitleKey)}
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
                        label={t('group.columns.name')}
                        rules={[
                            {required: true, message: t('group.columns.name')},
                            {max: 50, message: t('group.columns.name')}
                        ]}
                    >
                        <Input placeholder={t('group.columns.name')} maxLength={50}/>
                    </Form.Item>
                    <Form.Item
                        name="description"
                        label={t('group.columns.description')}
                        rules={[
                            {max: 200, message: t('group.columns.description')}
                        ]}
                    >
                        <Input.TextArea placeholder={t('group.columns.description')} rows={3} maxLength={200}/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default GroupManagementPage;
