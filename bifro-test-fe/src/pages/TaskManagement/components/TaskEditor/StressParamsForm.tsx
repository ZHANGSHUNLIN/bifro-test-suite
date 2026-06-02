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
import {Alert, Col, Collapse, Descriptions, Divider, Form, Input, InputNumber, Row, Select, Slider, Switch, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {TaskTemplateValues} from '../../../../features/task';
import {payloadPlaceholderGuide, validatePayloadTemplate} from '../../../../features/task/domain/templatePlaceholders';
import type {WaveformProfile} from '../../../../features/profile';

const {TextArea} = Input;
const {Text} = Typography;

interface StressParamsFormProps {
    form: any;
    currentTemplate: string;
    fanMode: 'default' | 'fanOut' | 'fanIn';
    onFanModeChange: (value: 'default' | 'fanOut' | 'fanIn') => void;
    profiles: WaveformProfile[];
}

// Whether template includes publish behavior
const hasPub = (t: string) => [
    TaskTemplateValues.PUBSUB_STANDARD,
    TaskTemplateValues.PUBSUB_PUB_ONLY,
    TaskTemplateValues.CONN_PUBLISH_ON_CONNECT,
].includes(t as any);

// Whether template includes subscribe behavior
const hasSub = (t: string) => [
    TaskTemplateValues.PUBSUB_STANDARD,
    TaskTemplateValues.PUBSUB_SUB_ONLY,
].includes(t as any);

const hasTopicsPerClient = (t: string) => [
    TaskTemplateValues.PUBSUB_STANDARD,
    TaskTemplateValues.PUBSUB_PUB_ONLY,
    TaskTemplateValues.PUBSUB_SUB_ONLY,
].includes(t as any);

const getPlaceholderGuide = (t: (key: string) => string) => [
    ...payloadPlaceholderGuide().map(({key, descKey}) => ({key, desc: t(descKey)})),
];

function fmtDuration(ms: number): string {
    const sec = Math.round(ms / 1000);
    if (sec < 60) return `${sec}s`;
    const min = Math.floor(sec / 60);
    const s = sec % 60;
    return s > 0 ? `${min}m ${s}s` : `${min}m`;
}

function formatProfileOptionLabel(t: (key: string, options?: Record<string, unknown>) => string, p: WaveformProfile): string {
    return t('task.form.profileOptionLabel', {
        name: p.name,
        peak: p.peakQps,
        duration: fmtDuration(p.totalDurationMs),
        integral: p.integral ?? '—',
    });
}

const StressParamsForm: React.FC<StressParamsFormProps> = ({
                                                               form,
                                                               currentTemplate,
                                                               fanMode,
                                                               onFanModeChange,
                                                               profiles,
                                                           }) => {
    const {t} = useTranslation();
    const payloadMode = Form.useWatch('payloadMode', form);
    // Connect/disconnect QPS mode (FIXED | DYNAMIC)
    const connectQpsMode = Form.useWatch('connectQpsMode', form);
    const disconnectQpsMode = Form.useWatch('disconnectQpsMode', form);
    const publishQpsMode = Form.useWatch('publishQpsMode', form);
    // Subscribe QPS mode (FIXED | DYNAMIC)
    const subscribeQpsMode = Form.useWatch('subscribeQpsMode', form);
    const taskType = Form.useWatch('taskType', form);

    const connectProfileId = Form.useWatch('connectProfileId', form);
    const disconnectProfileId = Form.useWatch('disconnectProfileId', form);
    const publishProfileId = Form.useWatch('publishProfileId', form);

    // Find profile by profileId
    const findProfile = (id?: string) => profiles.find(p => p.id === id);

    // Show warning when disconnect integral differs from connect integral
    const connectProfile = findProfile(connectProfileId);
    const disconnectProfile = findProfile(disconnectProfileId);
    const publishProfile = findProfile(publishProfileId);
    const integralMismatch =
        connectProfile && disconnectProfile &&
        connectProfile.integral != null && disconnectProfile.integral != null &&
        connectProfile.integral !== disconnectProfile.integral;

    const PLACEHOLDER_GUIDE = getPlaceholderGuide(t);

    return (
        <>
            {/* ── Client config (all templates) ── */}
            <Divider plain>{t('task.form.clientConfig')}</Divider>
            <Row gutter={16}>
                <Col span={8}>
                    <Form.Item
                        name="connectQpsMode"
                        label={t('task.form.connectQpsMode')}
                        initialValue="FIXED"
                        tooltip={t('task.form.connectQpsModeTooltip')}
                    >
                        <Select options={[
                            {label: t('task.form.fixedRate'), value: 'FIXED'},
                            {label: t('task.form.dynamicProfile'), value: 'DYNAMIC'},
                        ]}/>
                    </Form.Item>
                </Col>
                {connectQpsMode !== 'DYNAMIC' ? (
                    <>
                        <Col span={8}>
                            <Form.Item name="totalClientCount" label={t('task.form.clientCount')} initialValue={100}
                                       rules={[{required: true}]}>
                                <InputNumber min={1} style={{width: '100%'}}/>
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item name="connectRate" label={t('task.form.connectRatePerSec')} initialValue={100}>
                                <InputNumber min={1} step={1} style={{width: '100%'}}/>
                            </Form.Item>
                        </Col>
                    </>
                ) : (
                    <Col span={16}>
                        <Form.Item
                            name="connectProfileId"
                            label={t('task.form.connectProfile')}
                            rules={[{required: true, message: t('task.form.connectProfileRequired')}]}
                            tooltip={t('task.form.connectProfileTooltip')}
                        >
                            <Select
                                placeholder={t('task.form.selectProfile')}
                                showSearch
                                allowClear
                                filterOption={(input, opt) =>
                                    (opt?.label as string ?? '').toLowerCase().includes(input.toLowerCase())
                                }
                                options={profiles.map(p => ({
                                    label: formatProfileOptionLabel(t, p),
                                    value: p.id,
                                }))}
                            />
                        </Form.Item>
                    </Col>
                )}
            </Row>

            {/* Show integral after selecting connection profile (DYNAMIC mode) */}
            {connectQpsMode === 'DYNAMIC' && connectProfile && (
                <Row gutter={16} style={{marginBottom: 8}}>
                    <Col span={24}>
                        <Text type="secondary" style={{fontSize: 12}}>
                            {t('task.form.clientCountByProfile')}
                            <Text strong
                                  style={{color: '#1677ff'}}>{connectProfile.integral?.toLocaleString() ?? '—'}</Text>
                        </Text>
                    </Col>
                </Row>
            )}

            {/* Disconnect config */}
            <Row gutter={16}>
                <Col span={8}>
                    <Form.Item
                        name="disconnectQpsMode"
                        label={t('task.form.disconnectQpsMode')}
                        initialValue="FIXED"
                        tooltip={t('task.form.disconnectQpsModeTooltip')}
                    >
                        <Select options={[
                            {label: t('task.form.fixedRate'), value: 'FIXED'},
                            {label: t('task.form.dynamicProfile'), value: 'DYNAMIC'},
                        ]}/>
                    </Form.Item>
                </Col>
                {disconnectQpsMode !== 'DYNAMIC' && (
                    <Col span={8}>
                        <Form.Item name="disconnectRate" label={t('task.form.disconnectRatePerSec')} initialValue={2000}>
                            <InputNumber min={1} step={1} style={{width: '100%'}}/>
                        </Form.Item>
                    </Col>
                )}
            </Row>

            {/* Disconnect profile selector (DYNAMIC mode) */}
            {disconnectQpsMode === 'DYNAMIC' && (
                <>
                    <Row gutter={16} style={{marginBottom: 8}}>
                        <Col span={16}>
                            <Form.Item
                                name="disconnectProfileId"
                                label={t('task.form.disconnectProfile')}
                                rules={[{required: true, message: t('task.form.disconnectProfileRequired')}]}
                                tooltip={t('task.form.disconnectProfileTooltip')}
                            >
                                <Select
                                    placeholder={t('task.form.selectProfile')}
                                    showSearch
                                    allowClear
                                    filterOption={(input, opt) =>
                                        (opt?.label as string ?? '').toLowerCase().includes(input.toLowerCase())
                                    }
                                    options={profiles.map(p => ({
                                        label: formatProfileOptionLabel(t, p),
                                        value: p.id,
                                    }))}
                                />
                            </Form.Item>
                        </Col>
                        {disconnectProfile && (
                            <Col span={8} style={{display: 'flex', alignItems: 'center', paddingTop: 4}}>
                                <Text type="secondary" style={{fontSize: 12}}>
                                    {t('task.form.integralTotal')}<Text strong
                                                     style={{color: disconnectProfile.integral === connectProfile?.integral ? '#52c41a' : '#ff4d4f'}}>{disconnectProfile.integral?.toLocaleString() ?? '—'}</Text>
                                </Text>
                            </Col>
                        )}
                    </Row>
                    {integralMismatch && (
                        <Alert
                            type="error"
                            showIcon
                            style={{marginBottom: 16}}
                            message={t('task.form.integralMismatch')}
                            description={t('task.form.integralMismatchDesc', {
                                connectIntegral: connectProfile!.integral?.toLocaleString(),
                                disconnectIntegral: disconnectProfile!.integral?.toLocaleString(),
                            })}
                        />
                    )}
                </>
            )}

            {/* ── Time params (all templates) ── */}
            <Divider plain>{t('task.form.timingParams')}</Divider>
            <Row gutter={16}>
                {publishQpsMode === 'DYNAMIC' ? (
                    <Col span={8}>
                        <Descriptions
                            bordered
                            size="small"
                            column={1}
                            items={[{
                                key: 'publishProfileDuration',
                                label: t('task.form.stressDuration'),
                                children: (
                                    publishProfile
                                        ? <Text>{fmtDuration(publishProfile.totalDurationMs)} {t('task.form.durationByPublishProfile')}</Text>
                                        : <Text type="secondary">{t('task.form.durationPendingPublishProfile')}</Text>
                                ),
                            }]}
                        />
                    </Col>
                ) : (
                    <Col span={8}>
                        <Form.Item name="stressDurationInSec" label={t('task.form.stressDuration')} initialValue={60}>
                            <InputNumber min={1} style={{width: '100%'}}/>
                        </Form.Item>
                    </Col>
                )}
                <Col span={8}>
                    <Form.Item name="stageTimeoutInSec" label={t('task.form.stageTimeout')} initialValue={30}>
                        <InputNumber min={1} style={{width: '100%'}}/>
                    </Form.Item>
                </Col>
                <Col span={8}>
                    <Form.Item name="delayAfterStageInSec" label={t('task.form.delayAfterStage')} initialValue={30}>
                        <InputNumber min={0} style={{width: '100%'}}/>
                    </Form.Item>
                </Col>
            </Row>

            {/* ── Publish params (templates with publish behavior) ── */}
            {hasPub(currentTemplate) && (
                <>
                    <Divider plain>{t('task.form.publishParams')}</Divider>
                    <Row gutter={16}>
                        <Col span={8}>
                            <Form.Item name="messageSize" label={t('task.form.messageSize')} initialValue={32}
                                       rules={[{type: 'number', min: 1, max: 65536}]}>
                                <InputNumber style={{width: '100%'}}/>
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item
                                name="publishQpsMode"
                                label={t('task.form.publishQpsMode')}
                                initialValue="FIXED"
                                tooltip={t('task.form.publishQpsModeTooltip')}
                            >
                                <Select options={[
                                    {label: t('task.form.fixedRate'), value: 'FIXED'},
                                    {label: t('task.form.dynamicProfile'), value: 'DYNAMIC'},
                                ]}/>
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item
                                name="payloadMode"
                                label={t('task.form.payloadMode')}
                                initialValue="BIFRO"
                                tooltip={t('task.form.payloadModeTooltip')}
                            >
                                <Select options={[
                                    {label: t('task.form.payloadModeBifro'), value: 'BIFRO'},
                                    {label: t('task.form.payloadModeTemplate'), value: 'TEMPLATE'},
                                    {label: t('task.form.payloadModeRandom'), value: 'RANDOM'},
                                ]}/>
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row gutter={16}>
                        {publishQpsMode === 'DYNAMIC' ? (
                            <>
                                <Col span={16}>
                                    <Form.Item
                                        name="publishProfileId"
                                        label={t('task.form.publishProfile')}
                                        rules={[{required: true, message: t('task.form.publishProfileRequired')}]}
                                        tooltip={t('task.form.publishProfileTooltip')}
                                    >
                                        <Select
                                            placeholder={t('task.form.selectProfile')}
                                            showSearch
                                            allowClear
                                            filterOption={(input, opt) =>
                                                (opt?.label as string ?? '').toLowerCase().includes(input.toLowerCase())
                                            }
                                            options={profiles.map(p => ({
                                                label: formatProfileOptionLabel(t, p),
                                                value: p.id,
                                            }))}
                                        />
                                    </Form.Item>
                                </Col>
                                {publishProfile && (
                                    <Col span={8} style={{display: 'flex', alignItems: 'center', paddingTop: 4}}>
                                        <Text type="secondary" style={{fontSize: 12}}>
                                            {t('task.form.integral')}<Text strong
                                                       style={{color: '#1677ff'}}>{publishProfile.integral?.toLocaleString() ?? '—'}</Text>
                                        </Text>
                                    </Col>
                                )}
                            </>
                        ) : (
                            <Col span={8}>
                                <Form.Item name="publishRate" label={t('task.form.publishRate')} initialValue={1}
                                           rules={[{type: 'number', min: 0.001, max: 100000}]}
                                           tooltip={t('task.form.publishRateTooltip')}>
                                    <InputNumber min={0.001} max={100000} step={0.1} precision={3}
                                                 style={{width: '100%'}}/>
                                </Form.Item>
                            </Col>
                        )}
                    </Row>
                    <Row gutter={16}>
                        <Col span={8}>
                            <Form.Item
                                name="retain"
                                label={t('task.form.retain')}
                                tooltip={t('task.form.retainTooltip')}
                                valuePropName="checked"
                                initialValue={false}
                            >
                                <Switch/>
                            </Form.Item>
                        </Col>
                    </Row>

                    {/* Template mode: show template input and placeholder reference */}
                    {payloadMode === 'TEMPLATE' && (
                        <Row gutter={16}>
                            <Col span={24}>
                                <Form.Item
                                    name="payloadTemplate"
                                    label={t('task.form.msgTemplate')}
                                    rules={[
                                        {required: true, message: t('task.form.msgTemplateInputRequired')},
                                        {
                                            validator: (_, value) => {
                                                if (!value) return Promise.resolve();
                                                const result = validatePayloadTemplate(value);
                                                if (result.valid) {
                                                    return Promise.resolve();
                                                }
                                                if (result.reason === 'unclosed') {
                                                    return Promise.reject(t('task.form.unclosedPlaceholder'));
                                                }
                                                if (result.reason === 'unknown') {
                                                    return Promise.reject(t('task.form.unknownPlaceholder', {
                                                        ph: result.placeholder,
                                                    }));
                                                }
                                                return Promise.reject(t('task.form.msgTemplateRequired'));
                                            },
                                        },
                                    ]}
                                >
                                    <TextArea
                                        rows={4}
                                        placeholder={t('task.form.msgTemplateExample')}
                                    />
                                </Form.Item>
                                <Collapse
                                    ghost
                                    size="small"
                                    style={{marginTop: -12, marginBottom: 16}}
                                    items={[{
                                        key: 'guide',
                                        label: <Text type="secondary" style={{fontSize: 12}}>{t('task.form.placeholderGuide')}</Text>,
                                        children: (
                                            <table style={{fontSize: 12, width: '100%', borderCollapse: 'collapse'}}>
                                                <thead>
                                                <tr style={{background: '#fafafa'}}>
                                                    <th style={{
                                                        padding: '4px 8px',
                                                        textAlign: 'left',
                                                        border: '1px solid #f0f0f0'
                                                    }}>{t('task.form.placeholderCol')}
                                                    </th>
                                                    <th style={{
                                                        padding: '4px 8px',
                                                        textAlign: 'left',
                                                        border: '1px solid #f0f0f0'
                                                    }}>{t('task.form.placeholderDescCol')}
                                                    </th>
                                                </tr>
                                                </thead>
                                                <tbody>
                                                {PLACEHOLDER_GUIDE.map(({key, desc}) => (
                                                    <tr key={key}>
                                                        <td style={{
                                                            padding: '4px 8px',
                                                            fontFamily: 'monospace',
                                                            border: '1px solid #f0f0f0'
                                                        }}>{key}</td>
                                                        <td style={{
                                                            padding: '4px 8px',
                                                            border: '1px solid #f0f0f0'
                                                        }}>{desc}</td>
                                                    </tr>
                                                ))}
                                                </tbody>
                                            </table>
                                        ),
                                    }]}
                                />
                            </Col>
                        </Row>
                    )}

                    <Row gutter={16}>
                        {hasTopicsPerClient(currentTemplate) && (
                            <Col span={8}>
                                <Form.Item
                                    name="topicsPerClient"
                                    label={t('task.form.topicsPerClient')}
                                    initialValue={1}
                                    rules={[{type: 'number', min: 1, max: 1000}]}
                                    tooltip={t('task.form.topicsPerClientTooltip')}
                                >
                                    <InputNumber min={1} style={{width: '100%'}}/>
                                </Form.Item>
                            </Col>
                        )}
                        <Col span={16}>
                            {currentTemplate === TaskTemplateValues.PUBSUB_PUB_ONLY ? (
                                <Form.Item name="topic" label={t('task.form.pubTopic')}>
                                    <Input placeholder={t('task.form.pubTopicPlaceholder1')}/>
                                </Form.Item>
                            ) : currentTemplate === TaskTemplateValues.CONN_PUBLISH_ON_CONNECT ? (
                                <Form.Item name="topic" label={t('task.form.pubTopic')}>
                                    <Input placeholder={t('task.form.pubTopicPlaceholder2')}/>
                                </Form.Item>
                            ) : (
                                <Form.Item label={t('task.form.pubTopic')}>
                                    <Input disabled placeholder={t('task.form.pubTopicAuto')}
                                           style={{color: '#999'}}/>
                                </Form.Item>
                            )}
                        </Col>
                        {currentTemplate === TaskTemplateValues.CONN_PUBLISH_ON_CONNECT && (
                            <Col span={8}>
                                <Form.Item name="qos" label={t('task.form.qos')} initialValue={0}>
                                    <Select options={[
                                        {label: t('task.form.qos0'), value: 0},
                                        {label: t('task.form.qos1'), value: 1},
                                        {label: t('task.form.qos2'), value: 2},
                                    ]}/>
                                </Form.Item>
                            </Col>
                        )}
                    </Row>
                </>
            )}

            {/* ── Subscribe params (templates with subscribe behavior) ── */}
            {hasSub(currentTemplate) && (
                <>
                    <Divider plain>{t('task.form.subscribeParams')}</Divider>
                    <Row gutter={16}>
                        {currentTemplate === TaskTemplateValues.PUBSUB_SUB_ONLY ? (
                            <Col span={12}>
                                <Form.Item name="topic" label={t('task.form.subTopic')}
                                           rules={[{required: true, message: t('task.form.subTopicRequired')}]}>
                                    <Input placeholder={t('task.form.subTopicPlaceholder')}/>
                                </Form.Item>
                            </Col>
                        ) : (
                            <Col span={12}>
                                <Form.Item label={t('task.form.subTopic')}>
                                    <Input disabled placeholder={t('task.form.subTopicAuto')} style={{color: '#999'}}/>
                                </Form.Item>
                            </Col>
                        )}
                        {!hasPub(currentTemplate) && (
                            <Col span={4}>
                                <Form.Item
                                    name="topicsPerClient"
                                    label={t('task.form.topicsPerClient')}
                                    initialValue={1}
                                    rules={[{type: 'number', min: 1, max: 1000}]}
                                    tooltip={t('task.form.topicsPerClientTooltip')}
                                >
                                    <InputNumber min={1} style={{width: '100%'}}/>
                                </Form.Item>
                            </Col>
                        )}
                        <Col span={8}>
                            <Form.Item name="qos" label={t('task.form.qos')} initialValue={0}>
                                <Select options={[
                                    {label: t('task.form.qos0'), value: 0},
                                    {label: t('task.form.qos1'), value: 1},
                                    {label: t('task.form.qos2'), value: 2},
                                ]}/>
                            </Form.Item>
                        </Col>
                    </Row>

                    {/* Subscribe QPS control */}
                    <Row gutter={16}>
                        <Col span={8}>
                            <Form.Item
                                name="subscribeQpsMode"
                                label={t('task.form.subscribeQpsMode')}
                                initialValue="FIXED"
                                tooltip={t('task.form.subscribeQpsModeTooltip')}
                            >
                                <Select options={[
                                    {label: t('task.form.fixedRate'), value: 'FIXED'},
                                    {label: t('task.form.dynamicProfile'), value: 'DYNAMIC'},
                                ]}/>
                            </Form.Item>
                        </Col>
                        {subscribeQpsMode !== 'DYNAMIC' && (
                            <Col span={8}>
                                <Form.Item
                                    name="subscribeRate"
                                    label={t('task.form.subscribeRatePerSec')}
                                    initialValue={100}
                                    tooltip={t('task.form.subscribeRateTooltip')}
                                >
                                    <InputNumber min={0} step={100} style={{width: '100%'}}/>
                                </Form.Item>
                            </Col>
                        )}
                    </Row>

                    {/* Subscribe profile selector (DYNAMIC mode) */}
                    {subscribeQpsMode === 'DYNAMIC' && (
                        <Row gutter={16} style={{marginBottom: 8}}>
                            <Col span={16}>
                                <Form.Item
                                    name="subscribeProfileId"
                                    label={t('task.form.subscribeProfile')}
                                    rules={[{required: true, message: t('task.form.subscribeProfileRequired')}]}
                                    tooltip={t('task.form.subscribeProfileTooltip')}
                                >
                                    <Select
                                        placeholder={t('task.form.selectProfile')}
                                        showSearch
                                        allowClear
                                        filterOption={(input, opt) =>
                                            (opt?.label as string ?? '').toLowerCase().includes(input.toLowerCase())
                                        }
                                        options={profiles.map(p => ({
                                            label: formatProfileOptionLabel(t, p),
                                            value: p.id,
                                        }))}
                                    />
                                </Form.Item>
                            </Col>
                            {(() => {
                                const subProfileId = form.getFieldValue('subscribeProfileId');
                                const subProfile = findProfile(subProfileId);
                                const effectiveClientCount = connectQpsMode === 'DYNAMIC'
                                    ? (connectProfile?.integral ?? 0)
                                    : (form.getFieldValue('totalClientCount') ?? 0);
                                const overLimit = subProfile?.integral != null && subProfile.integral > effectiveClientCount;
                                return subProfile ? (
                                    <Col span={8} style={{display: 'flex', alignItems: 'center', paddingTop: 4}}>
                                        <Text type="secondary" style={{fontSize: 12}}>
                                            {t('task.form.integral')}<Text strong
                                                       style={{color: overLimit ? '#faad14' : '#1677ff'}}>{subProfile.integral?.toLocaleString() ?? '—'}</Text>
                                            {overLimit && <Text type="warning" style={{
                                                fontSize: 11,
                                                marginLeft: 4
                                            }}>{t('task.form.integralOverLimit')}</Text>}
                                        </Text>
                                    </Col>
                                ) : null;
                            })()}
                        </Row>
                    )}

                    {currentTemplate === TaskTemplateValues.PUBSUB_STANDARD && (
                        <>
                            <Row gutter={16}>
                                <Col span={8}>
                                    <Form.Item name="fanMode" label={t('task.form.fanMode')} initialValue="default">
                                        <Select
                                            options={[
                                                {label: t('task.form.fanModeDefault'), value: 'default'},
                                                {label: t('task.form.fanModeOut'), value: 'fanOut'},
                                                {label: t('task.form.fanModeIn'), value: 'fanIn'},
                                            ]}
                                            onChange={onFanModeChange}
                                        />
                                    </Form.Item>
                                </Col>
                                {fanMode === 'fanOut' && (
                                    <Col span={8}>
                                        <Form.Item name="fanOut" label={t('task.form.fanOutMultiplier')} initialValue={2}
                                                   rules={[{type: 'number', min: 2, max: 100}]}>
                                            <InputNumber min={2} style={{width: '100%'}}/>
                                        </Form.Item>
                                    </Col>
                                )}
                                {fanMode === 'fanIn' && (
                                    <Col span={8}>
                                        <Form.Item name="fanIn" label={t('task.form.fanInMultiplier')} initialValue={2}
                                                   rules={[{type: 'number', min: 2, max: 100}]}>
                                            <InputNumber min={2} style={{width: '100%'}}/>
                                        </Form.Item>
                                    </Col>
                                )}
                            </Row>
                            <Alert
                                type="info"
                                showIcon
                                style={{marginBottom: 16}}
                                message={t('task.form.pubsubRemainderNotice')}
                            />
                        </>
                    )}
                </>
            )}

            {/* ── Chaos test config (CHAOS task type only) ── */}
            {taskType === 'CHAOS' && (
                <>
                    <Divider plain>{t('task.form.chaosParams')}</Divider>
                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item
                                name={['chaosPolicy', 'behaviors']}
                                label={t('task.form.chaosBehaviors')}
                                tooltip={t('task.form.chaosBehaviorsTooltip')}
                                rules={[{required: true, message: t('task.form.chaosBehaviorsRequired')}]}
                            >
                                <Select mode="multiple" placeholder={t('task.form.chaosBehaviorsPlaceholder')}>
                                    <Select.Option value="DUPLICATE_PUBACK">
                                        DUPLICATE_PUBACK — duplicate ACK for same packetId
                                    </Select.Option>
                                    <Select.Option value="EXCEED_INFLIGHT_WINDOW">
                                        EXCEED_INFLIGHT_WINDOW — exceed inflight window
                                    </Select.Option>
                                    <Select.Option value="DOUBLE_CONNECT">
                                        DOUBLE_CONNECT — send CONNECT again while already connected
                                    </Select.Option>
                                    <Select.Option value="INVALID_PACKET_ID_ZERO">
                                        INVALID_PACKET_ID_ZERO — QoS1 PUBLISH packetId=0
                                    </Select.Option>
                                    <Select.Option value="OVERSIZED_PAYLOAD">
                                        OVERSIZED_PAYLOAD — exceed broker max packet size
                                    </Select.Option>
                                    <Select.Option value="MALFORMED_TOPIC">
                                        MALFORMED_TOPIC — PUBLISH topic contains illegal wildcards
                                    </Select.Option>
                                </Select>
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item
                                name={['chaosPolicy', 'targetRatio']}
                                label={t('task.form.chaosRatio')}
                                tooltip={t('task.form.chaosRatioTooltip')}
                                initialValue={0.1}
                                rules={[{required: true, type: 'number', min: 0.01, max: 1.0}]}
                            >
                                <Slider min={0.01} max={1.0} step={0.01}
                                        marks={{0.1: '10%', 0.5: '50%', 1.0: '100%'}}/>
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item
                                name={['chaosPolicy', 'maxInflight']}
                                label={t('task.form.maxInflight')}
                                tooltip={t('task.form.maxInflightTooltip')}
                                initialValue={65535}
                            >
                                <InputNumber min={1} max={65535} style={{width: '100%'}}/>
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row gutter={16}>
                        <Col span={8}>
                            <Form.Item
                                name={['chaosPolicy', 'maxPacketSizeOverride']}
                                label={t('task.form.maxPacketSize')}
                                tooltip={t('task.form.maxPacketSizeTooltip')}
                                initialValue={0}
                            >
                                <InputNumber min={0} style={{width: '100%'}}/>
                            </Form.Item>
                        </Col>
                    </Row>
                </>
            )}

        </>
    );
};

export default StressParamsForm;
