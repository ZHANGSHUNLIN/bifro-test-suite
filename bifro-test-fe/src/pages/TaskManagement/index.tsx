import { useState } from 'react';
import { Card, Tabs } from 'antd';
import { ClusterOutlined, TeamOutlined } from '@ant-design/icons';
import TaskListPage from './TaskListPage';
import TaskGroupManagement from '../TaskGroupManagement';

interface TabItem {
    key: string;
    label: string;
    icon: React.ReactNode;
}

const TaskManagement: React.FC = () => {
    const [activeTab, setActiveTab] = useState('tasks');

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
                    onChange={(key) => setActiveTab(key as string)}
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
