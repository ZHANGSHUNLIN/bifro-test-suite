import { useState, useEffect } from 'react';
import { Card, Tabs } from 'antd';
import { ClusterOutlined, TeamOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import MqttBrokerManagement from './MqttBrokerManagement';
import MqttGroupManagement from './MqttGroupManagement';

interface TabItem {
    key: string;
    label: string;
    icon: React.ReactNode;
}

const MqttInstanceManagement: React.FC = () => {
    const [searchParams, setSearchParams] = useSearchParams();
    const tabFromUrl = searchParams.get('tab');
    const [activeTab, setActiveTab] = useState(tabFromUrl || 'instances');

    // 监听 URL 参数变化，同步更新 activeTab
    useEffect(() => {
        const tab = searchParams.get('tab');
        if (tab && (tab === 'instances' || tab === 'groups')) {
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
                    onChange={handleTabChange}
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
