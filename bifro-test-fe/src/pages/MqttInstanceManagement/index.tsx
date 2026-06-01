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

import React, {useEffect, useState} from 'react';
import {Card, Tabs} from 'antd';
import {ClusterOutlined, TeamOutlined} from '@ant-design/icons';
import {useSearchParams} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import MqttBrokerManagement from '../MqttBrokerManagement';
import MqttGroupManagement from '../MqttGroupManagement';

interface TabItem {
    key: string;
    label: string;
    icon: React.ReactNode;
}

const MqttInstanceManagement: React.FC = () => {
    const [searchParams, setSearchParams] = useSearchParams();
    const {t} = useTranslation();
    const tabFromUrl = searchParams.get('tab');
    const [activeTab, setActiveTab] = useState(tabFromUrl || 'instances');

    // Listen for URL param changes, sync activeTab
    useEffect(() => {
        const tab = searchParams.get('tab');
        if (tab && (tab === 'instances' || tab === 'groups')) {
            setActiveTab(tab);
        }
    }, [searchParams]);

    // Update URL param when switching tabs
    const handleTabChange = (key: string) => {
        setActiveTab(key);
        setSearchParams({tab: key});
    };

    const tabItems: TabItem[] = [
        {
            key: 'instances',
            label: t('broker.tabs.instances'),
            icon: <ClusterOutlined/>,
        },
        {
            key: 'groups',
            label: t('broker.tabs.groups'),
            icon: <TeamOutlined/>,
        },
    ];

    return (
        <div>
            <div style={{marginBottom: 24}}>
                <Tabs
                    activeKey={activeTab}
                    items={tabItems}
                    onChange={handleTabChange}
                />
            </div>
            <Card>
                {activeTab === 'instances' && <MqttBrokerManagement/>}
                {activeTab === 'groups' && <MqttGroupManagement/>}
            </Card>
        </div>
    );
};

export default MqttInstanceManagement;
