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

import React from 'react';
import {Button, Col, Divider, Form, Input, InputNumber, Row, Select, Switch, Tag} from 'antd';
import {SettingOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';

interface GroupOption {
    label: string;
    value: string;
}

interface BrokerOption {
    brokerId: string;
    name: string;
    host: string;
    port: number;
    group?: string;
}

interface AuthBrokerFormProps {
    authType: string;
    certEnabled: boolean;
    brokers: BrokerOption[];
    brokerLoading: boolean;
    brokerGroupSelectOptions: GroupOption[];
    clientCertOptions: GroupOption[];
    selectedBrokerGroup: string;
    onAuthTypeChange: (value: string) => void;
    onCertEnabledChange: (value: boolean) => void;
    onBrokerGroupChange: (value: string | undefined) => void;
    onNavigateToBrokerGroups: () => void;
    onNavigateToCertificates: () => void;
}

const AuthBrokerForm: React.FC<AuthBrokerFormProps> = ({
                                                           authType,
                                                           certEnabled,
                                                           brokers,
                                                           brokerLoading,
                                                           brokerGroupSelectOptions,
                                                           clientCertOptions,
                                                           selectedBrokerGroup,
                                                           onAuthTypeChange,
                                                           onCertEnabledChange,
                                                           onBrokerGroupChange,
                                                           onNavigateToBrokerGroups,
                                                           onNavigateToCertificates,
                                                       }) => {
    const {t} = useTranslation();
    return (
        <>
            <Divider plain>{t('task.form.authConfig')}</Divider>
            <Row gutter={16}>
                <Col span={10}>
                    <Form.Item name="protocol" label={t('task.form.protocolType')} initialValue="mqtt" rules={[{required: true}]}>
                        <Select options={[
                            {label: 'MQTT', value: 'mqtt'},
                            {label: 'MQTTS', value: 'mqtts'},
                            {label: 'WS', value: 'ws', disabled: true},
                            {label: 'WSS', value: 'wss', disabled: true},
                        ]}/>
                    </Form.Item>
                </Col>
                <Col span={10}>
                    <Form.Item name="authType" label={t('task.form.authType')} initialValue="none">
                        <Select
                            options={[
                                {label: t('task.form.noAuth'), value: 'none'},
                                {label: t('task.form.normalAuth'), value: 'normal'},
                                {label: 'BYOC', value: 'byoc'},
                                {label: t('task.form.iotCoreAuth'), value: 'iotCore', disabled: true},
                            ]}
                            onChange={onAuthTypeChange}
                        />
                    </Form.Item>
                </Col>
                <Col span={4}>
                    <Form.Item name="clientCertEnabled" label={t('task.form.enableCert')} valuePropName="checked"
                               initialValue={false}>
                        <Switch onChange={onCertEnabledChange}/>
                    </Form.Item>
                </Col>
            </Row>

            {/* Auth details */}
            {authType === 'normal' && (
                <Row gutter={16}>
                    <Col span={12}>
                        <Form.Item name="username" label={t('task.form.username')}>
                            <Input placeholder={t('task.form.usernamePlaceholder')}/>
                        </Form.Item>
                    </Col>
                    <Col span={12}>
                        <Form.Item name="password" label={t('task.form.password')}>
                            <Input.Password placeholder={t('task.form.passwordPlaceholder')}/>
                        </Form.Item>
                    </Col>
                </Row>
            )}
            {authType === 'byoc' && (
                <Row gutter={16}>
                    <Col span={8}>
                        <Form.Item name="tenantId" label={t('task.form.tenantId')}
                                   rules={[{required: true, message: t('task.form.tenantIdRequired')}]}>
                            <Input placeholder={t('task.form.tenantIdPlaceholder')}/>
                        </Form.Item>
                    </Col>
                    <Col span={8}>
                        <Form.Item name="thingIdPrefix" label={t('task.form.thingIdPrefix')}>
                            <Input placeholder={t('task.form.thingIdPrefixPlaceholder')}/>
                        </Form.Item>
                    </Col>
                    <Col span={8}>
                        <Form.Item name="thingIdStartAt" label={t('task.form.thingIdStartAt')} initialValue={0}>
                            <InputNumber min={0} style={{width: '100%'}}/>
                        </Form.Item>
                    </Col>
                </Row>
            )}

            {certEnabled && (
                <Form.Item
                    name="clientCertId"
                    label={t('task.form.selectClientCert')}
                    extra={
                        <a onClick={onNavigateToCertificates} style={{fontSize: 12}}>
                            {t('task.form.manageCerts')}
                        </a>
                    }
                    rules={[{required: true, message: t('task.form.selectClientCertRequired')}]}
                >
                    <Select
                        placeholder={t('task.form.selectClientCertPlaceholder')}
                        options={clientCertOptions}
                    />
                </Form.Item>
            )}

            <Divider plain>{t('task.form.brokerConfig')}</Divider>
            <Row gutter={16}>
                <Col span={12}>
                    <Form.Item label={`${t('task.form.brokerGroup')}${t('task.form.brokerGroupFilterSuffix')}`} extra={t('task.form.brokerGroupHint')}>
                        <Select
                            placeholder={t('task.form.selectBrokerGroupPlaceholder')}
                            value={selectedBrokerGroup || undefined}
                            onChange={onBrokerGroupChange}
                            allowClear
                            options={brokerGroupSelectOptions}
                            dropdownRender={(menu) => (
                                <>
                                    {menu}
                                    <Button type="link" icon={<SettingOutlined/>} style={{fontSize: 12}}
                                            onClick={onNavigateToBrokerGroups}>
                                        {t('task.form.manageBrokerGroups')}
                                    </Button>
                                </>
                            )}
                        />
                    </Form.Item>
                </Col>
                <Col span={12}>
                    <Form.Item
                        name="brokers"
                        label={t('task.form.selectBroker')}
                        rules={[{required: true, message: t('task.form.selectBrokerRequired')}]}
                    >
                        <Select
                            mode="multiple"
                            placeholder={t('task.form.selectBrokerPlaceholder')}
                            loading={brokerLoading}
                            options={brokers.map(broker => ({
                                value: broker.brokerId,
                                label: (
                                    <span>
                    {broker.name} ({broker.host}:{broker.port})
                                        {broker.group && brokerGroupSelectOptions.find(opt => opt.value === broker.group) && (
                                            <Tag color="blue" style={{marginLeft: 8}}>
                                                {brokerGroupSelectOptions.find(opt => opt.value === broker.group)?.label}
                                            </Tag>
                                        )}
                  </span>
                                )
                            }))}
                        />
                    </Form.Item>
                </Col>
            </Row>
        </>
    );
};

export default AuthBrokerForm;