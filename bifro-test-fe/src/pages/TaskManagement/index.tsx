import { useState, useEffect } from 'react';
import { Card, Tabs } from 'antd';
import { ClusterOutlined, TeamOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import TaskListPage from './TaskListPage';
import TaskGroupManagement from '../TaskGroupManagement';

interface TabItem {
    key: string;
    label: string;
    icon: React.ReactNode;
}

const TaskManagement: React.FC = () => {
    const [searchParams, setSearchParams] = useSearchParams();
    const tabFromUrl = searchParams.get('tab');
    const [activeTab, setActiveTab] = useState(tabFromUrl || 'tasks');

    // 监听 URL 参数变化，同步更新 activeTab
    useEffect(() => {
        const tab = searchParams.get('tab');
        if (tab && (tab === 'tasks' || tab === 'groups')) {
            setActiveTab(tab);
        }
    }, [searchParams]);

    // 切换 Tab 时更新 URL 参数
    const handleTabChange = (key: string) => {
        setActiveTab(key);
        setSearchParams({ tab: key });
    };

    const tabItems: TabItem[] = [
        {
            key: 'tasks',
            label: '任务管理',
            icon: <ClusterOutlined />,
        },
        {
            key: 'groups',
            label: '分组管理',
            icon: <TeamOutlined />,
        },
    ];

    return (
        <div>
            <div style={{ marginBottom: 24 }}>
                <Tabs
                    activeKey={activeTab}
                    items={tabItems}
                    onChange={handleTabChange}
                />
            </div>
            <Card>
                {activeTab === 'tasks' && <TaskListPage />}
                {activeTab === 'groups' && <TaskGroupManagement />}
            </Card>
        </div>
    );
};

export default TaskManagement;
