import React, { useState } from 'react';
import {
    MenuFoldOutlined,
    MenuUnfoldOutlined,
    UserOutlined,
    DashboardOutlined,
    FileTextOutlined,
    SettingOutlined,
    LogoutOutlined,
    ClusterOutlined,
    TeamOutlined
} from '@ant-design/icons';
import {
    Layout,
    Menu,
    Button,
    theme,
    Avatar,
    Dropdown,
    Breadcrumb,
    Space,
    Typography
} from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';

const { Header, Sider, Content } = Layout;
const { Title } = Typography;

type MenuItem = {
    key: string;
    icon: React.ReactNode;
    label: string;
    path?: string;
};

const MainLayout: React.FC = () => {
    const [collapsed, setCollapsed] = useState(false);
    const navigate = useNavigate();
    const location = useLocation();
    const {
        token: { colorBgContainer, borderRadiusLG },
    } = theme.useToken();

    // 用户下拉菜单
    const userMenuItems = [
        {
            key: 'profile',
            label: '个人中心',
            icon: <UserOutlined />
        },
        {
            key: 'settings',
            label: '设置',
            icon: <SettingOutlined />
        },
        {
            type: 'divider' as const,
        },
        {
            key: 'logout',
            label: '退出登录',
            icon: <LogoutOutlined />
        },
    ];

    // 侧边栏菜单配置
    const menuItems: MenuItem[] = [
        {
            key: '/',
            icon: <DashboardOutlined />,
            label: '首页',
            path: '/'
        },
        {
            key: '/tasks',
            icon: <FileTextOutlined />,
            label: '任务管理',
            path: '/tasks'
        },
        {
            key: '/mqtt-brokers',
            icon: <ClusterOutlined />,
            label: 'Broker 管理',
            path: '/mqtt-brokers'
        },
        {
            key: '/cluster',
            icon: <TeamOutlined />,
            label: '集群管理',
            path: '/cluster'
        }
    ];

    const flatMenuItems = menuItems;

    // 获取当前面包屑路径
    const getBreadcrumbItems = () => {
        const pathSnippets = location.pathname.split('/').filter(i => i);
        const breadcrumbItems = [];

        for (let i = 0; i < pathSnippets.length; i++) {
            const url = `/${pathSnippets.slice(0, i + 1).join('/')}`;
            const menuItem = flatMenuItems.find(item => item.key === url);
            if (menuItem) {
                breadcrumbItems.push({
                    title: menuItem.label
                });
            }
        }

        return breadcrumbItems;
    };

    // 处理菜单点击
    const handleMenuClick = ({ key }: { key: string }) => {
        navigate(key);
    };

    // 处理用户菜单点击
    const handleUserMenuClick = ({ key }: { key: string }) => {
        switch (key) {
            case 'logout':
                // 处理退出登录逻辑
                console.log('退出登录');
                break;
            case 'profile':
                navigate('/profile');
                break;
            case 'settings':
                navigate('/settings');
                break;
        }
    };

    // 找到当前选中的菜单项
    const findSelectedKey = (): string[] => {
        const currentPath = location.pathname;

        // 精确匹配
        const exactMatch = flatMenuItems.find(item => item.path === currentPath);
        if (exactMatch) return [exactMatch.key];

        // 模糊匹配
        const pathParts = currentPath.split('/').filter(Boolean);
        for (let i = pathParts.length; i > 0; i--) {
            const path = '/' + pathParts.slice(0, i).join('/');
            const match = flatMenuItems.find(item => item.path === path);
            if (match) return [match.key];
        }

        return [];
    };

    // 找到打开的父菜单
    const findOpenKeys = (): string[] => {
        return [];
    };

    return (
        <Layout style={{ minHeight: '100vh' }}>
            {/* 侧边栏 */}
            <Sider
                trigger={null}
                collapsible
                collapsed={collapsed}
                width={250}
                style={{
                    background: colorBgContainer,
                    overflow: 'auto',
                    height: '100vh',
                    position: 'fixed',
                    left: 0,
                    top: 0,
                    bottom: 0,
                    boxShadow: '2px 0 8px 0 rgba(29, 35, 41, 0.05)'
                }}
            >
                {/* Logo */}
                <div style={{
                    height: 64,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderBottom: '1px solid #f0f0f0'
                }}>
                    {collapsed ? (
                        <Title level={3} style={{ margin: 0, color: '#1890ff' }}>A</Title>
                    ) : (
                        <Title level={3} style={{ margin: 0, color: '#1890ff' }}>Bifro Test Suite</Title>
                    )}
                </div>

                {/* 菜单 */}
                <Menu
                    mode="inline"
                    selectedKeys={findSelectedKey()}
                    defaultOpenKeys={findOpenKeys()}
                    items={menuItems}
                    onClick={handleMenuClick}
                    style={{
                        borderRight: 0,
                        marginTop: 8
                    }}
                />
            </Sider>

            {/* 主要内容区域 */}
            <Layout style={{ marginLeft: collapsed ? 80 : 250, transition: 'all 0.2s' }}>
                {/* 顶部 Header */}
                <Header style={{
                    padding: '0 24px',
                    background: colorBgContainer,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    boxShadow: '0 1px 4px rgba(0, 21, 41, 0.08)',
                    position: 'sticky',
                    top: 0,
                    zIndex: 1
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                        <Button
                            type="text"
                            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                            onClick={() => setCollapsed(!collapsed)}
                            style={{ fontSize: '16px' }}
                        />

                        {/* 面包屑 */}
                        <Breadcrumb
                            items={getBreadcrumbItems()}
                            style={{ marginLeft: 16 }}
                        />
                    </div>

                    {/* 右侧用户信息 */}
                    <Space size="large">
                        <Dropdown
                            menu={{
                                items: userMenuItems,
                                onClick: handleUserMenuClick
                            }}
                            placement="bottomRight"
                        >
                            <Space style={{ cursor: 'pointer' }}>
                                <Avatar icon={<UserOutlined />} />
                                <span>管理员</span>
                            </Space>
                        </Dropdown>
                    </Space>
                </Header>

                {/* 内容区域 */}
                <Content style={{
                    margin: '24px 16px',
                    padding: 24,
                    background: colorBgContainer,
                    borderRadius: borderRadiusLG,
                    minHeight: 280,
                    overflow: 'auto'
                }}>
                    <Outlet />
                </Content>

                {/* Footer */}
                <div style={{
                    textAlign: 'center',
                    padding: '16px 24px',
                    color: 'rgba(0, 0, 0, 0.45)',
                    borderTop: '1px solid #f0f0f0',
                    marginLeft: 24
                }}>
                    Bifro Test Suite ©2026 Created with Ant Design
                </div>
            </Layout>
        </Layout>
    );
};

export default MainLayout;