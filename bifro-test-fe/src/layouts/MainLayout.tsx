import React, { useState } from 'react';
import {
    Layout,
    Menu,
    Breadcrumb,
    Typography,
    theme,
    Button,
} from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
    MenuFoldOutlined,
    MenuUnfoldOutlined,
} from '@ant-design/icons';

const { Header, Sider, Content } = Layout;
const { Title } = Typography;

type MenuItem = {
    key: string;
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

    // 侧边栏菜单配置
    const menuItems: MenuItem[] = [
        {
            key: '/',
            label: '首页',
            path: '/'
        },
        {
            key: '/tasks',
            label: '任务管理',
            path: '/tasks'
        },
        {
            key: '/mqtt-instances',
            label: 'broker实例管理',
            path: '/mqtt-instances'
        },
        {
            key: '/cluster',
            label: '集群管理',
            path: '/cluster'
        }
    ];

    // 获取当前面包屑路径
    const getBreadcrumbItems = () => {
        const pathSnippets = location.pathname.split('/').filter(i => i);
        type BreadcrumbItem = { title: string; key: string };
        const breadcrumbItems: BreadcrumbItem[] = [];
        for (let i = 0; i < pathSnippets.length; i++) {
            const url = `/${pathSnippets.slice(0, i + 1).join('/')}`;
            const menuItem = menuItems.find(item => item.key === url);
            if (menuItem) {
                breadcrumbItems.push({
                    title: menuItem.label,
                    key: menuItem.key
                });
            }
        }
        return breadcrumbItems;
    };

    // 处理菜单点击
    const handleMenuClick = ({ key }: { key: string }) => {
        navigate(key);
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
                    selectedKeys={getBreadcrumbItems().map(item => item.key)}
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
