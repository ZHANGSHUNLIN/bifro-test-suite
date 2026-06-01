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

import React, {useCallback, useEffect, useState} from 'react';
import {Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tag, message} from 'antd';
import {DeleteOutlined, EditOutlined, KeyOutlined, PlusOutlined, ReloadOutlined} from '@ant-design/icons';
import type {ColumnsType} from 'antd/es/table';
import dayjs from 'dayjs';
import {useTranslation} from 'react-i18next';
import systemUserApi from '../../features/systemUser';
import type {CreateSystemUserRequest, SystemRole, SystemUser} from '../../features/systemUser';
import {useTablePagination} from '../../hooks/useTablePagination';

const ROLE_OPTIONS: { label: string; value: SystemRole }[] = [
    {label: 'ADMIN', value: 'ADMIN'},
    {label: 'VIEWER', value: 'VIEWER'},
];

const SystemUserManagement: React.FC = () => {
    const {t} = useTranslation();
    const [messageApi, contextHolder] = message.useMessage();
    const [users, setUsers] = useState<SystemUser[]>([]);
    const [loading, setLoading] = useState(false);
    const [editorOpen, setEditorOpen] = useState(false);
    const [passwordOpen, setPasswordOpen] = useState(false);
    const [editingUser, setEditingUser] = useState<SystemUser | null>(null);
    const [passwordUser, setPasswordUser] = useState<SystemUser | null>(null);
    const [form] = Form.useForm<CreateSystemUserRequest>();
    const [passwordForm] = Form.useForm<{ password: string }>();
    const {
        currentPage,
        pageSize,
        applyPageInfo,
        getPageAfterDelete,
        getTablePagination,
    } = useTablePagination({defaultPageSize: 20, totalLabel: t('common.total')});

    const loadUsers = useCallback(async (page: number, size: number) => {
        setLoading(true);
        try {
            const pageInfo = await systemUserApi.list(page, size);
            setUsers(pageInfo.content || []);
            applyPageInfo(pageInfo, page, size);
        } catch (error) {
            messageApi.error(error instanceof Error ? error.message : t('user.loadFailed'));
        } finally {
            setLoading(false);
        }
    }, [applyPageInfo, messageApi, t]);

    useEffect(() => {
        loadUsers(1, 20);
    }, [loadUsers]);

    const openCreate = () => {
        setEditingUser(null);
        form.setFieldsValue({roles: ['VIEWER'], enabled: true} as CreateSystemUserRequest);
        setEditorOpen(true);
    };

    const openEdit = (user: SystemUser) => {
        setEditingUser(user);
        form.setFieldsValue({
            username: user.username,
            roles: user.roles,
            enabled: user.enabled,
        } as CreateSystemUserRequest);
        setEditorOpen(true);
    };

    const submitEditor = async () => {
        const values = await form.validateFields();
        try {
            if (editingUser) {
                await systemUserApi.update(editingUser.id, {
                    roles: values.roles,
                    enabled: values.enabled,
                });
                messageApi.success(t('user.updateSuccess'));
            } else {
                await systemUserApi.create(values);
                messageApi.success(t('user.createSuccess'));
            }
            setEditorOpen(false);
            await loadUsers(editingUser ? currentPage : 1, pageSize);
        } catch (error) {
            messageApi.error(error instanceof Error ? error.message : t('common.operationFailed'));
        }
    };

    const submitPassword = async () => {
        if (!passwordUser) return;
        const values = await passwordForm.validateFields();
        try {
            await systemUserApi.resetPassword(passwordUser.id, {password: values.password});
            messageApi.success(t('user.resetPasswordSuccess'));
            setPasswordOpen(false);
            passwordForm.resetFields();
        } catch (error) {
            messageApi.error(error instanceof Error ? error.message : t('common.operationFailed'));
        }
    };

    const deleteUser = async (user: SystemUser) => {
        try {
            await systemUserApi.delete(user.id);
            messageApi.success(t('user.deleteSuccess'));
            await loadUsers(getPageAfterDelete(), pageSize);
        } catch (error) {
            messageApi.error(error instanceof Error ? error.message : t('common.operationFailed'));
        }
    };

    const columns: ColumnsType<SystemUser> = [
        {
            title: t('auth.username'),
            dataIndex: 'username',
            width: 180,
        },
        {
            title: t('user.roles'),
            dataIndex: 'roles',
            width: 180,
            render: (roles: string[]) => (
                <Space size={4} wrap>
                    {roles.map(role => <Tag key={role} color={role === 'ADMIN' ? 'red' : 'blue'}>{role}</Tag>)}
                </Space>
            ),
        },
        {
            title: t('common.status'),
            dataIndex: 'enabled',
            width: 120,
            render: (enabled: boolean) => (
                <Tag color={enabled ? 'success' : 'default'}>
                    {enabled ? t('common.enable') : t('common.disable')}
                </Tag>
            ),
        },
        {
            title: t('common.createdAt'),
            dataIndex: 'createdAt',
            width: 180,
            render: (value: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
        },
        {
            title: t('common.updatedAt'),
            dataIndex: 'updatedAt',
            width: 180,
            render: (value: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
        },
        {
            title: t('common.actions'),
            width: 260,
            render: (_, record) => (
                <Space size={4}>
                    <Button type="link" icon={<EditOutlined/>} onClick={() => openEdit(record)}>
                        {t('common.edit')}
                    </Button>
                    <Button
                        type="link"
                        icon={<KeyOutlined/>}
                        onClick={() => {
                            setPasswordUser(record);
                            setPasswordOpen(true);
                        }}
                    >
                        {t('user.resetPassword')}
                    </Button>
                    <Popconfirm title={t('common.deleteConfirm')} onConfirm={() => deleteUser(record)}>
                        <Button type="link" danger icon={<DeleteOutlined/>}>
                            {t('common.delete')}
                        </Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <Card>
            {contextHolder}
            <div style={{display: 'flex', justifyContent: 'flex-end', gap: 8, marginBottom: 16}}>
                <Button icon={<ReloadOutlined/>} onClick={() => loadUsers(currentPage, pageSize)}>
                    {t('common.refresh')}
                </Button>
                <Button type="primary" icon={<PlusOutlined/>} onClick={openCreate}>{t('user.createUser')}</Button>
            </div>
            <Table
                rowKey="id"
                columns={columns}
                dataSource={users}
                loading={loading}
                size="small"
                scroll={{x: 1100}}
                pagination={getTablePagination(loadUsers)}
            />

            <Modal
                open={editorOpen}
                title={editingUser ? t('user.editUser') : t('user.createUser')}
                onOk={submitEditor}
                onCancel={() => setEditorOpen(false)}
                destroyOnHidden
            >
                <Form form={form} layout="vertical" requiredMark={false}>
                    <Form.Item
                        name="username"
                        label={t('auth.username')}
                        rules={[{required: true, message: t('auth.usernameRequired')}]}
                    >
                        <Input disabled={!!editingUser}/>
                    </Form.Item>
                    {!editingUser && (
                        <Form.Item
                            name="password"
                            label={t('auth.password')}
                            rules={[{required: true, message: t('auth.passwordRequired')}]}
                        >
                            <Input.Password/>
                        </Form.Item>
                    )}
                    <Form.Item
                        name="roles"
                        label={t('user.roles')}
                        rules={[{required: true, message: t('user.rolesRequired')}]}
                    >
                        <Select mode="multiple" options={ROLE_OPTIONS}/>
                    </Form.Item>
                    <Form.Item name="enabled" label={t('common.status')} valuePropName="checked">
                        <Switch checkedChildren={t('common.enable')} unCheckedChildren={t('common.disable')}/>
                    </Form.Item>
                </Form>
            </Modal>

            <Modal
                open={passwordOpen}
                title={t('user.resetPassword')}
                onOk={submitPassword}
                onCancel={() => setPasswordOpen(false)}
                destroyOnHidden
            >
                <Form form={passwordForm} layout="vertical" requiredMark={false}>
                    <Form.Item
                        name="password"
                        label={t('user.newPassword')}
                        rules={[{required: true, message: t('user.newPasswordRequired')}]}
                    >
                        <Input.Password/>
                    </Form.Item>
                </Form>
            </Modal>
        </Card>
    );
};

export default SystemUserManagement;
