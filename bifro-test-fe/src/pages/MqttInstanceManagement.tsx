import { useState } from 'react';
import { Card, Tabs } from 'antd';
import { ClusterOutlined, TeamOutlined } from '@ant-design/icons';
import MqttBrokerManagement from './MqttBrokerManagement';
import MqttGroupManagement from './MqttGroupManagement';

interface TabItem {
    key: string;
    label: string;
    icon: React.ReactNode;
}

const MqttInstanceManagement: React.FC = () => {
    const [activeTab, setActiveTab] = useState('instances');

    const tabItems: TabItem[] = [
        {
            key: 'instances',
            label: '实例管理',
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
                {activeTab === 'instances' && <MqttBrokerManagement />}
                {activeTab === 'groups' && <MqttGroupManagement />}
            </Card>
        </div>
    );
};

export default MqttInstanceManagement;
