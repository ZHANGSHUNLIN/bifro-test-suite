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

import React, {useEffect, useMemo, useState} from 'react';
import {Avatar, Breadcrumb, Button, Dropdown, Form, Input, Layout, Menu, Modal, Space, Tag, theme, Tooltip, Typography, message} from 'antd';
import type {MenuProps} from 'antd';
import {Outlet, useLocation, useNavigate} from 'react-router-dom';
import {
    AuditOutlined,
    CloudServerOutlined,
    ClusterOutlined,
    DashboardOutlined,
    KeyOutlined,
    LogoutOutlined,
    LineChartOutlined,
    MenuFoldOutlined,
    MenuUnfoldOutlined,
    MoonOutlined,
    SafetyCertificateOutlined,
    SettingOutlined,
    SunOutlined,
    UnorderedListOutlined,
    UserOutlined,
} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import {useTheme} from '../contexts/ThemeContext';
import {useAuth} from '../contexts/AuthContext';
import LanguageSwitcher from '../components/LanguageSwitcher';
import authApi from '../features/auth';
import {api} from '../utils/request';

const {Header, Sider, Content} = Layout;
const {Text} = Typography;

const SIDER_BG = '#1a1f2e';
const SIDER_WIDTH = 220;
const SIDER_COLLAPSED_WIDTH = 64;
const APP_TITLE = 'Bifro Test Suite';

type MenuItem = { key: string; label: string; path?: string; icon?: React.ReactNode; children?: MenuItem[] };

const MainLayout: React.FC = () => {
    const {t} = useTranslation();
    const [collapsed, setCollapsed] = useState(false);
    const [beVersion, setBeVersion] = useState<string>('...');
    const [passwordOpen, setPasswordOpen] = useState(false);
    const [passwordForm] = Form.useForm<{ oldPassword: string; newPassword: string; confirmPassword: string }>();
    const [messageApi, contextHolder] = message.useMessage();
    const navigate = useNavigate();
    const location = useLocation();
    const {toggleTheme, isDark} = useTheme();
    const {user, logout, hasRole} = useAuth();
    const {token} = theme.useToken();

    const menuItems: MenuItem[] = useMemo(() => [
        {key: '/', label: t('nav.home'), path: '/', icon: <DashboardOutlined/>},
        {key: '/tasks', label: t('nav.taskManagement'), path: '/tasks', icon: <UnorderedListOutlined/>},
        {key: '/profiles', label: t('nav.trafficProfile'), path: '/profiles', icon: <LineChartOutlined/>},
        {key: '/mqtt-instances', label: t('nav.broker'), path: '/mqtt-instances', icon: <CloudServerOutlined/>},
        {key: '/certificates', label: t('nav.certificates'), path: '/certificates', icon: <SafetyCertificateOutlined/>},
        {key: '/cluster', label: t('nav.clusterNodes'), path: '/cluster', icon: <ClusterOutlined/>},
        ...(hasRole('ADMIN') ? [
            {
                key: '/system',
                label: t('nav.systemManagement'),
                icon: <SettingOutlined/>,
                children: [
                    {key: '/system/users', label: t('nav.userManagement'), path: '/system/users', icon: <UserOutlined/>},
                    {key: '/system/audit-logs', label: t('nav.auditLogs'), path: '/system/audit-logs', icon: <AuditOutlined/>},
                ],
            },
        ] : []),
    ], [hasRole, t]);

    const breadcrumbNameMap: Record<string, string> = useMemo(() => ({
        '/tasks': t('nav.taskManagement'),
        '/profiles': t('nav.trafficProfile'),
        '/mqtt-instances': t('nav.broker'),
        '/certificates': t('nav.certificates'),
        '/cluster': t('nav.clusterNodes'),
        '/system': t('nav.systemManagement'),
        '/system/users': t('nav.userManagement'),
        '/system/audit-logs': t('nav.auditLogs'),
    }), [t]);

    useEffect(() => {
        api.get<{ version: string }>('/version')
            .then(data => setBeVersion(data.version))
            .catch(() => setBeVersion('be-?'));
    }, []);

    const selectedChildKey = menuItems.flatMap(item => item.children || [])
        .find(item => item.key === location.pathname)?.key;
    const selectedKey = selectedChildKey || menuItems.find(item =>
        item.key !== '/' ? location.pathname.startsWith(item.key) : location.pathname === '/'
    )?.key || '/';

    const breadcrumbItems = (() => {
        const parts = location.pathname.split('/').filter(Boolean);
        const items = [{title: t('common.home'), key: '/'}];
        let path = '';
        for (const part of parts) {
            path += `/${part}`;
            const name = breadcrumbNameMap[path];
            if (name) items.push({title: name, key: path});
        }
        return items.length > 1 ? items.slice(1) : [];
    })();

    useEffect(() => {
        const pageTitle = selectedKey === '/' ? t('nav.home') : breadcrumbNameMap[location.pathname];
        document.title = pageTitle ? `${pageTitle} - ${APP_TITLE}` : APP_TITLE;
    }, [breadcrumbNameMap, location.pathname, selectedKey, t]);

    const userMenuItems: MenuProps['items'] = [
        {
            key: 'roles',
            disabled: true,
            label: (
                <Space wrap size={4}>
                    {(user?.roles || []).map(role => <Tag key={role}>{role}</Tag>)}
                </Space>
            ),
        },
        {type: 'divider'},
        {
            key: 'change-password',
            icon: <KeyOutlined/>,
            label: t('auth.changePassword'),
        },
        {type: 'divider'},
        {
            key: 'logout',
            icon: <LogoutOutlined/>,
            label: t('auth.logout'),
        },
    ];

    const handleUserMenuClick: MenuProps['onClick'] = async ({key}) => {
        if (key === 'change-password') {
            setPasswordOpen(true);
        } else if (key === 'logout') {
            await logout();
            navigate('/login', {replace: true});
        }
    };

    const submitChangePassword = async () => {
        const values = await passwordForm.validateFields();
        try {
            await authApi.changePassword({
                oldPassword: values.oldPassword,
                newPassword: values.newPassword,
            });
            messageApi.success(t('auth.changePasswordSuccess'));
            setPasswordOpen(false);
            passwordForm.resetFields();
        } catch (error) {
            messageApi.error(error instanceof Error ? error.message : t('common.operationFailed'));
        }
    };

    return (
        <Layout style={{minHeight: '100vh', background: token.colorBgLayout}}>
            {contextHolder}
            {/* ── Sidebar (always dark) ── */}
            <Sider
                trigger={null}
                collapsible
                collapsed={collapsed}
                width={SIDER_WIDTH}
                collapsedWidth={SIDER_COLLAPSED_WIDTH}
                style={{
                    background: SIDER_BG,
                    overflow: 'auto',
                    height: '100vh',
                    position: 'fixed',
                    left: 0,
                    top: 0,
                    bottom: 0,
                    zIndex: 100,
                    boxShadow: '2px 0 12px rgba(0,0,0,0.25)',
                }}
            >
                {/* Logo */}
                <div style={{
                    height: 56,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: collapsed ? 'center' : 'flex-start',
                    padding: collapsed ? 0 : '0 20px',
                    borderBottom: '1px solid rgba(255,255,255,0.06)',
                    flexShrink: 0,
                    overflow: 'hidden',
                }}>
                    <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        cursor: 'pointer',
                    }} onClick={() => navigate('/')}>
                        <div style={{
                            width: 28,
                            height: 28,
                            borderRadius: 6,
                            background: 'linear-gradient(135deg, #00b173 0%, #00d68a 100%)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: 14,
                            fontWeight: 800,
                            color: '#fff',
                            flexShrink: 0,
                            boxShadow: '0 2px 8px rgba(0,177,115,0.4)',
                        }}>B
                        </div>
                        {!collapsed && (
                            <span style={{
                                color: '#ffffff',
                                fontSize: 14,
                                fontWeight: 600,
                                letterSpacing: 0.3,
                                whiteSpace: 'nowrap',
                            }}>
                                Bifro Suite
                            </span>
                        )}
                    </div>
                </div>

                {/* Menu */}
                <Menu
                    theme="dark"
                    mode="inline"
                    selectedKeys={[selectedKey]}
                    defaultOpenKeys={['/system']}
                    items={menuItems.map(item => ({
                        key: item.key,
                        label: item.label,
                        icon: item.icon,
                        children: item.children?.map(child => ({
                            key: child.key,
                            label: child.label,
                            icon: child.icon,
                        })),
                    }))}
                    onClick={({key}) => navigate(key)}
                    style={{
                        background: SIDER_BG,
                        borderRight: 'none',
                        marginTop: 8,
                        fontSize: 13,
                    }}
                />

                {/* Bottom collapse button */}
                <div style={{
                    position: 'absolute',
                    bottom: 0,
                    left: 0,
                    right: 0,
                    height: 48,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: collapsed ? 'center' : 'flex-end',
                    padding: collapsed ? 0 : '0 16px',
                    borderTop: '1px solid rgba(255,255,255,0.06)',
                }}>
                    <Button
                        type="text"
                        icon={collapsed ? <MenuUnfoldOutlined/> : <MenuFoldOutlined/>}
                        onClick={() => setCollapsed(!collapsed)}
                        style={{color: 'rgba(255,255,255,0.45)', fontSize: 14}}
                    />
                </div>
            </Sider>

            {/* ── Main content area ── */}
            <Layout style={{
                marginLeft: collapsed ? SIDER_COLLAPSED_WIDTH : SIDER_WIDTH,
                transition: 'margin-left 0.2s',
                minHeight: '100vh',
                background: token.colorBgLayout,
            }}>
                {/* Header */}
                <Header style={{
                    padding: '0 24px',
                    background: token.colorBgContainer,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    height: 56,
                    lineHeight: '56px',
                    position: 'sticky',
                    top: 0,
                    zIndex: 99,
                    borderBottom: `1px solid ${token.colorBorderSecondary}`,
                    boxShadow: isDark ? 'none' : '0 1px 4px rgba(0,21,41,0.06)',
                }}>
                    <Breadcrumb
                        items={[
                            {title: <span style={{color: token.colorTextTertiary, fontSize: 13}}>{t('common.home')}</span>},
                            ...breadcrumbItems.map(item => ({
                                title: <span style={{fontSize: 13}}>{item.title}</span>,
                            })),
                        ]}
                    />

                    <Space size={8} align="center" style={{lineHeight: 1}}>
                        <LanguageSwitcher variant="button"/>
                        {user?.enabled && (
                            <Dropdown menu={{items: userMenuItems, onClick: handleUserMenuClick}} trigger={['click']}>
                                <Button
                                    type="text"
                                    style={{
                                        height: 32,
                                        padding: '0 8px',
                                        display: 'flex',
                                        alignItems: 'center',
                                        lineHeight: 1,
                                    }}
                                >
                                    <Space size={6} align="center" style={{lineHeight: 1}}>
                                        <Avatar size={22} style={{background: token.colorPrimary, flexShrink: 0}}>
                                            {(user.username || '?').slice(0, 1).toUpperCase()}
                                        </Avatar>
                                        <Text style={{maxWidth: 120, lineHeight: '22px'}} ellipsis>
                                            {user.username || t('auth.anonymous')}
                                        </Text>
                                    </Space>
                                </Button>
                            </Dropdown>
                        )}
                        <Tooltip title={isDark ? t('common.lightMode') : t('common.darkMode')}>
                            <Button
                                type="text"
                                size="small"
                                icon={isDark ? <SunOutlined/> : <MoonOutlined/>}
                                onClick={toggleTheme}
                                style={{
                                    color: token.colorTextSecondary,
                                    fontSize: 15,
                                    width: 32,
                                    height: 32,
                                }}
                            />
                        </Tooltip>
                    </Space>
                </Header>

                {/* Content */}
                <Content style={{
                    margin: '20px 20px 0',
                    padding: 0,
                    minHeight: 'calc(100vh - 56px - 48px)',
                }}>
                    <Outlet/>
                </Content>

                {/* Footer */}
                <div style={{
                    textAlign: 'center',
                    padding: '14px 24px',
                    fontSize: 12,
                    color: token.colorTextTertiary,
                    borderTop: `1px solid ${token.colorBorderSecondary}`,
                }}>
                    Bifro Test Suite &copy;2026
                    <span style={{margin: '0 8px', opacity: 0.4}}>|</span>
                    <Tooltip title={t('common.frontendBuildVersion')}>
                        <span style={{fontFamily: 'monospace', cursor: 'default'}}>{__FE_VERSION__}</span>
                    </Tooltip>
                    <span style={{margin: '0 6px', opacity: 0.4}}>·</span>
                    <Tooltip title={t('common.backendServiceVersion')}>
                        <span style={{fontFamily: 'monospace', cursor: 'default'}}>{beVersion}</span>
                    </Tooltip>
                </div>
            </Layout>
            <Modal
                open={passwordOpen}
                title={t('auth.changePassword')}
                onOk={submitChangePassword}
                onCancel={() => setPasswordOpen(false)}
                destroyOnHidden
            >
                <Form form={passwordForm} layout="vertical" requiredMark={false}>
                    <Form.Item
                        name="oldPassword"
                        label={t('auth.oldPassword')}
                        rules={[{required: true, message: t('auth.oldPasswordRequired')}]}
                    >
                        <Input.Password/>
                    </Form.Item>
                    <Form.Item
                        name="newPassword"
                        label={t('auth.newPassword')}
                        rules={[{required: true, message: t('auth.newPasswordRequired')}]}
                    >
                        <Input.Password/>
                    </Form.Item>
                    <Form.Item
                        name="confirmPassword"
                        label={t('auth.confirmPassword')}
                        dependencies={['newPassword']}
                        rules={[
                            {required: true, message: t('auth.confirmPasswordRequired')},
                            ({getFieldValue}) => ({
                                validator(_, value) {
                                    if (!value || getFieldValue('newPassword') === value) {
                                        return Promise.resolve();
                                    }
                                    return Promise.reject(new Error(t('auth.passwordMismatch')));
                                },
                            }),
                        ]}
                    >
                        <Input.Password/>
                    </Form.Item>
                </Form>
            </Modal>
        </Layout>
    );
};

export default MainLayout;
