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
import {Col, Divider, Form, Input, InputNumber, Row, Select, Switch} from 'antd';
import {useTranslation} from 'react-i18next';

interface AdvancedConfigFormProps {
    willEnabled: boolean;
    onWillEnabledChange: (value: boolean) => void;
}

const AdvancedConfigForm: React.FC<AdvancedConfigFormProps> = ({
                                                                   willEnabled,
                                                                   onWillEnabledChange,
                                                               }) => {
    const {t} = useTranslation();
    return (
        <>
            {/* ── Protocol options ── */}
            <Divider plain>{t('task.form.protocolOptions')}</Divider>
            <Row gutter={16}>
                <Col span={6}>
                    <Form.Item name="cleanSession" label={t('task.form.cleanSession')}
                               valuePropName="checked" initialValue={true}>
                        <Switch/>
                    </Form.Item>
                </Col>
                <Col span={6}>
                    <Form.Item name="mqtt5" label={t('task.form.mqtt5')}
                               valuePropName="checked" initialValue={false}>
                        <Switch/>
                    </Form.Item>
                </Col>
                <Col span={6}>
                    <Form.Item
                        name="emptyClientId"
                        label={t('task.form.emptyClientId')}
                        tooltip={t('task.form.emptyClientIdTooltip')}
                        valuePropName="checked"
                        initialValue={false}
                    >
                        <Switch/>
                    </Form.Item>
                </Col>
            </Row>
            <Row gutter={16}>
                <Col span={6}>
                    <Form.Item name="autoMultiAddress" label={t('task.form.autoMultiAddress')}
                               valuePropName="checked" initialValue={true}>
                        <Switch/>
                    </Form.Item>
                </Col>
            </Row>

            {/* ── Connection params ── */}
            <Divider plain>{t('task.form.connParams')}</Divider>
            <Row gutter={16}>
                <Col span={8}>
                    <Form.Item name="keepAliveInSec" label={t('task.form.keepAlive')} initialValue={120}>
                        <InputNumber min={0} max={3600} style={{width: '100%'}}/>
                    </Form.Item>
                </Col>
                <Col span={8}>
                    <Form.Item name="expiryIntervalInSec" label={t('task.form.expiryInterval')} initialValue={120}>
                        <InputNumber min={0} max={86400} style={{width: '100%'}}/>
                    </Form.Item>
                </Col>
                <Col span={8}>
                    <Form.Item
                        name="maxInflightQueue"
                        label={t('task.form.maxQueueSize')}
                        tooltip={t('task.form.maxQueueSizeTooltip')}
                        initialValue={200}
                    >
                        <InputNumber min={10} max={10000} style={{width: '100%'}}/>
                    </Form.Item>
                </Col>
            </Row>

            {/* ── Timeout & reconnect ── */}
            <Divider plain>{t('task.form.timeoutAndReconnect')}</Divider>
            <Row gutter={16}>
                <Col span={6}>
                    <Form.Item name="connectTimeoutInMs" label={t('task.form.connectTimeout')} initialValue={10000}>
                        <InputNumber min={100} max={60000} style={{width: '100%'}}/>
                    </Form.Item>
                </Col>
                <Col span={6}>
                    <Form.Item name="ackTimeoutInSec" label={t('task.form.ackTimeout')} initialValue={120}>
                        <InputNumber min={1} max={3600} style={{width: '100%'}}/>
                    </Form.Item>
                </Col>
                <Col span={6}>
                    <Form.Item name="reconnectMaxAttempts" label={t('task.form.reconnectMaxAttempts')} initialValue={2}>
                        <InputNumber min={1} max={100} style={{width: '100%'}}/>
                    </Form.Item>
                </Col>
                <Col span={6}>
                    <Form.Item name="reconnectIntervalInMs" label={t('task.form.reconnectInterval')} initialValue={5000}>
                        <InputNumber min={100} max={30000} style={{width: '100%'}}/>
                    </Form.Item>
                </Col>
            </Row>

            {/* ── Will config ── */}
            <Divider plain>{t('task.form.willConfig')}</Divider>
            <Row gutter={16}>
                <Col span={6}>
                    <Form.Item name={['willConfig', 'willFlag']} label={t('task.form.enableWill')}
                               valuePropName="checked" initialValue={false}>
                        <Switch onChange={onWillEnabledChange}/>
                    </Form.Item>
                </Col>
            </Row>
            {willEnabled && (
                <>
                    <Row gutter={16}>
                        <Col span={16}>
                            <Form.Item name={['willConfig', 'willTopic']} label={t('task.form.willTopic')}
                                       rules={[{required: true, message: t('task.form.willTopicRequired')}]}>
                                <Input placeholder={t('task.form.willTopicPlaceholder')}/>
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item name={['willConfig', 'willQos']} label={t('task.form.willQos')}
                                       initialValue={0}>
                                <Select options={[
                                    {label: t('task.form.qos0'), value: 0},
                                    {label: t('task.form.qos1'), value: 1},
                                    {label: t('task.form.qos2'), value: 2},
                                ]}/>
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row gutter={16}>
                        <Col span={16}>
                            <Form.Item name={['willConfig', 'willMessage']} label={t('task.form.willMessage')}
                                       rules={[{required: true, message: t('task.form.willMessageRequired')}]}>
                                <Input placeholder={t('task.form.willMessagePlaceholder')}/>
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item name={['willConfig', 'willRetain']} label={t('task.form.willRetain')}
                                       valuePropName="checked" initialValue={false}>
                                <Switch/>
                            </Form.Item>
                        </Col>
                    </Row>
                </>
            )}
        </>
    );
};

export default AdvancedConfigForm;
