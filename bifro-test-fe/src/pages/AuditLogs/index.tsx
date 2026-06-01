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

import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {Button, Card, DatePicker, Form, Input, Select, Space, Table, Tag, Typography, message} from 'antd';
import {ReloadOutlined, SearchOutlined} from '@ant-design/icons';
import type {ColumnsType} from 'antd/es/table';
import dayjs, {type Dayjs} from 'dayjs';
import {useTranslation} from 'react-i18next';
import auditApi from '../../features/audit';
import type {AuditLog, AuditLogQuery} from '../../features/audit';
import {useTablePagination} from '../../hooks/useTablePagination';

const {RangePicker} = DatePicker;
const {Text} = Typography;

interface AuditFilterValues {
    username?: string;
    action?: string;
    resourceType?: string;
    success?: boolean;
    timeRange?: [Dayjs, Dayjs];
}

const AuditLogs: React.FC = () => {
    const {t} = useTranslation();
    const [form] = Form.useForm<AuditFilterValues>();
    const [messageApi, contextHolder] = message.useMessage();
    const [logs, setLogs] = useState<AuditLog[]>([]);
    const [loading, setLoading] = useState(false);
    const {
        currentPage,
        pageSize,
        applyPageInfo,
        getTablePagination,
    } = useTablePagination({defaultPageSize: 20, totalLabel: t('common.total')});

    const actionOptions = useMemo(() => [
        'AUTH_LOGIN_SUCCESS',
        'AUTH_LOGIN_FAILURE',
        'AUTH_LOGOUT',
        'TASK_CREATE',
        'TASK_UPDATE',
        'TASK_START',
        'TASK_STOP',
        'TASK_DELETE',
        'TASK_ALLOCATE',
        'BROKER_CREATE',
        'BROKER_UPDATE',
        'BROKER_DELETE',
        'GROUP_CREATE',
        'GROUP_UPDATE',
        'GROUP_DELETE',
        'PROFILE_CREATE',
        'PROFILE_UPDATE',
        'PROFILE_DELETE',
        'CERT_CREATE',
        'CERT_UPDATE',
        'CERT_DELETE',
        'USER_CREATE',
        'USER_UPDATE',
        'USER_DELETE',
        'USER_RESET_PASSWORD',
        'USER_CHANGE_PASSWORD',
    ].map(value => ({label: value, value})), []);

    const resourceOptions = useMemo(() => ['AUTH', 'TASK', 'BROKER', 'GROUP', 'PROFILE', 'CERTIFICATE', 'USER']
        .map(value => ({label: value, value})), []);

    const buildQuery = useCallback((page: number, size: number): AuditLogQuery => {
        const values = form.getFieldsValue();
        return {
            username: values.username?.trim(),
            action: values.action,
            resourceType: values.resourceType,
            success: values.success,
            startTime: values.timeRange?.[0]?.toISOString(),
            endTime: values.timeRange?.[1]?.toISOString(),
            pageNum: page,
            pageSize: size,
        };
    }, [form]);

    const loadLogs = useCallback(async (page: number, size: number) => {
        setLoading(true);
        try {
            const data = await auditApi.list(buildQuery(page, size));
            setLogs(data.content || []);
            applyPageInfo(data, page, size);
        } catch (error) {
            messageApi.error(error instanceof Error ? error.message : t('audit.loadFailed'));
        } finally {
            setLoading(false);
        }
    }, [applyPageInfo, buildQuery, messageApi, t]);

    useEffect(() => {
        loadLogs(1, 20);
    }, [loadLogs]);

    const columns: ColumnsType<AuditLog> = [
        {
            title: t('audit.columns.time'),
            dataIndex: 'createdAt',
            width: 180,
            render: (value: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
        },
        {
            title: t('audit.columns.user'),
            dataIndex: 'username',
            width: 140,
            render: (value: string) => value || '-',
        },
        {
            title: t('audit.columns.action'),
            dataIndex: 'action',
            width: 210,
            render: (value: string) => value ? <Tag color="blue">{value}</Tag> : '-',
        },
        {
            title: t('audit.columns.resource'),
            width: 220,
            render: (_, record) => (
                <Space direction="vertical" size={0}>
                    <Text>{record.resourceType || '-'}</Text>
                    {record.resourceId && <Text type="secondary" style={{fontSize: 12}}>{record.resourceId}</Text>}
                </Space>
            ),
        },
        {
            title: t('audit.columns.result'),
            dataIndex: 'success',
            width: 110,
            render: (success: boolean) => (
                <Tag color={success ? 'success' : 'error'}>
                    {success ? t('common.success') : t('common.failed')}
                </Tag>
            ),
        },
        {
            title: t('audit.columns.clientIp'),
            dataIndex: 'clientIp',
            width: 140,
            render: (value: string) => value || '-',
        },
        {
            title: t('audit.columns.message'),
            dataIndex: 'message',
            ellipsis: true,
            render: (value: string) => value || '-',
        },
    ];

    return (
        <Card>
            {contextHolder}
            <Form
                form={form}
                layout="inline"
                onFinish={() => loadLogs(1, pageSize)}
                style={{marginBottom: 16, gap: 8}}
            >
                <Form.Item name="username">
                    <Input allowClear placeholder={t('audit.filters.username')} style={{width: 150}}/>
                </Form.Item>
                <Form.Item name="action">
                    <Select
                        allowClear
                        showSearch
                        placeholder={t('audit.filters.action')}
                        options={actionOptions}
                        style={{width: 220}}
                    />
                </Form.Item>
                <Form.Item name="resourceType">
                    <Select
                        allowClear
                        placeholder={t('audit.filters.resourceType')}
                        options={resourceOptions}
                        style={{width: 150}}
                    />
                </Form.Item>
                <Form.Item name="success">
                    <Select
                        allowClear
                        placeholder={t('audit.filters.result')}
                        options={[
                            {label: t('common.success'), value: true},
                            {label: t('common.failed'), value: false},
                        ]}
                        style={{width: 130}}
                    />
                </Form.Item>
                <Form.Item name="timeRange">
                    <RangePicker showTime style={{width: 340}}/>
                </Form.Item>
                <Space>
                    <Button type="primary" htmlType="submit" icon={<SearchOutlined/>}>
                        {t('common.search')}
                    </Button>
                    <Button
                        onClick={() => {
                            form.resetFields();
                            loadLogs(1, pageSize);
                        }}
                    >
                        {t('common.reset')}
                    </Button>
                    <Button icon={<ReloadOutlined/>} onClick={() => loadLogs(currentPage, pageSize)}>
                        {t('common.refresh')}
                    </Button>
                </Space>
            </Form>
            <Table
                rowKey="id"
                columns={columns}
                dataSource={logs}
                loading={loading}
                size="small"
                scroll={{x: 1220}}
                pagination={getTablePagination(loadLogs)}
            />
        </Card>
    );
};

export default AuditLogs;
