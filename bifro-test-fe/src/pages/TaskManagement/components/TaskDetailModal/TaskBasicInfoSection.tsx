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
import {Alert, Badge, Descriptions, Divider, Tag} from 'antd';
import {useTranslation} from 'react-i18next';
import type {TaskConfig, TaskDetailResponse} from '../../../../features/task';
import {TaskTemplateValues} from '../../../../features/task';
import {formatDateTime, getStatusColor, getStatusText} from '../../../../utils/taskUtils';

interface GroupOption {
    label: string;
    value: string;
}

interface TaskBasicInfoSectionProps {
    taskDetail: TaskDetailResponse | null;
    taskConfig: TaskConfig | null;
    groupOptions: GroupOption[];
}

interface TaskConfigPanelProps extends TaskBasicInfoSectionProps {
    taskConfig: TaskConfig | null;
}

const hasPub = (template?: string) => [
    TaskTemplateValues.PUBSUB_STANDARD,
    TaskTemplateValues.PUBSUB_PUB_ONLY,
    TaskTemplateValues.CONN_PUBLISH_ON_CONNECT,
].includes((template || '') as any);

const hasSub = (template?: string) => [
    TaskTemplateValues.PUBSUB_STANDARD,
    TaskTemplateValues.PUBSUB_SUB_ONLY,
].includes((template || '') as any);

const hasTopicsPerClient = (template?: string) => [
    TaskTemplateValues.PUBSUB_STANDARD,
    TaskTemplateValues.PUBSUB_PUB_ONLY,
    TaskTemplateValues.PUBSUB_SUB_ONLY,
].includes((template || '') as any);

const generatedTopicPattern = (mainTask: TaskConfig): string | null => {
    const topicsPerClient = hasTopicsPerClient(mainTask.template) ? (mainTask.topicsPerClient || 1) : 1;
    const withTopicOffset = (base: string) => topicsPerClient > 1 ? `${base}/{topicOffset}` : base;
    if (mainTask.topic) {
        return withTopicOffset(mainTask.topic);
    }
    if (!hasPub(mainTask.template) && !hasSub(mainTask.template)) {
        return null;
    }
    const fanIn = mainTask.fanIn || 1;
    const fanOut = mainTask.fanOut || 1;
    if (fanIn > 1) {
        return withTopicOffset(`${mainTask.taskId || '{taskId}'}/{floor(globalClientIndex / ${fanIn})}`);
    }
    if (fanOut > 1) {
        return withTopicOffset(`${mainTask.taskId || '{taskId}'}/{floor(globalClientIndex / ${fanOut})}`);
    }
    return withTopicOffset(`${mainTask.taskId || '{taskId}'}/{globalClientIndex}`);
};

const topicOffsetPattern = (mainTask: TaskConfig): string | null => {
    const topicsPerClient = hasTopicsPerClient(mainTask.template) ? (mainTask.topicsPerClient || 1) : 1;
    return topicsPerClient > 1 ? `topicOffset = 0..${topicsPerClient - 1}` : null;
};

// ─── Status overview bar (shared by three Tabs) ─────────────────────────────────────────────
const StatusBar: React.FC<{ taskDetail: TaskDetailResponse; taskConfig: TaskConfig }> = ({taskDetail, taskConfig}) => {
    const {t} = useTranslation();
    const mainTask = taskConfig;
    const statusColor = getStatusColor(mainTask.taskWorkStage ?? '');
    const statusText = getStatusText(mainTask.taskWorkStage ?? '');
    const serverAddr = taskDetail.brokers?.map(b => `${b.host}:${b.port}`).join(', ') || '-';

    const taskTypeLabel = mainTask.taskType === 'CONN' ? t('task.typeLabel.CONN') :
        mainTask.taskType === 'CHAOS' ? t('task.typeLabel.CHAOS') :
            mainTask.template === TaskTemplateValues.PUBSUB_PUB_ONLY ? t('task.templateLabel.PUBSUB_PUB_ONLY') :
                mainTask.template === TaskTemplateValues.PUBSUB_SUB_ONLY ? t('task.templateLabel.PUBSUB_SUB_ONLY') : t('task.typeLabel.PUBSUB');

    return (
        <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 0,
            padding: '12px 16px',
            background: '#fafafa',
            borderRadius: 6,
            border: '1px solid #f0f0f0',
            marginBottom: 20,
            flexWrap: 'wrap',
        }}>
            <div style={{display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '0 20px'}}>
                <span style={{fontSize: 11, color: '#999', marginBottom: 4}}>{t('task.detail.basicInfo.currentStatus')}</span>
                <Badge
                    status={statusColor === 'success' ? 'success' : statusColor === 'error' ? 'error' : statusColor === 'processing' ? 'processing' : statusColor === 'warning' ? 'warning' : 'default'}
                    text={<span style={{fontWeight: 600, fontSize: 14}}>{statusText}</span>}
                />
            </div>
            <Divider type="vertical" style={{height: 40, margin: '0 4px'}}/>
            <div style={{display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '0 20px'}}>
                <span style={{fontSize: 11, color: '#999', marginBottom: 4}}>{t('task.detail.basicInfo.clientCount')}</span>
                <span style={{fontSize: 20, fontWeight: 700, color: '#1677ff', lineHeight: 1}}>
                    {mainTask.totalClientCount?.toLocaleString() || '0'}
                </span>
            </div>
            <Divider type="vertical" style={{height: 40, margin: '0 4px'}}/>
            <div style={{display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '0 20px'}}>
                <span style={{fontSize: 11, color: '#999', marginBottom: 4}}>{t('task.detail.basicInfo.taskType')}</span>
                <Tag color={mainTask.taskType === 'CONN' ? 'blue' : 'purple'} style={{margin: 0}}>
                    {taskTypeLabel}
                </Tag>
            </div>
            <Divider type="vertical" style={{height: 40, margin: '0 4px'}}/>
            <div style={{display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '0 20px'}}>
                <span style={{fontSize: 11, color: '#999', marginBottom: 4}}>{t('task.detail.basicInfo.duration')}</span>
                <span style={{fontSize: 14, fontWeight: 500}}>{mainTask.stressDurationInSec || 60} {t('task.detail.basicInfo.seconds')}</span>
            </div>
            <Divider type="vertical" style={{height: 40, margin: '0 4px'}}/>
            <div style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                padding: '0 20px',
                flex: 1,
                minWidth: 120
            }}>
                <span style={{fontSize: 11, color: '#999', marginBottom: 4}}>{t('task.detail.basicInfo.serverAddr')}</span>
                <span style={{
                    fontSize: 13,
                    color: '#595959',
                    wordBreak: 'break-all',
                    textAlign: 'center'
                }}>{serverAddr}</span>
            </div>
        </div>
    );
};

const SectionTitle: React.FC<{ label: string; color: string }> = ({label, color}) => (
    <div style={{
        fontSize: 13,
        fontWeight: 600,
        color: '#333',
        marginBottom: 8,
        paddingLeft: 8,
        borderLeft: `3px solid ${color}`
    }}>
        {label}
    </div>
);

// ─── Tab 1: Task config (corresponds to first Tab in create modal) ──────────────────────────────
export const TaskConfigPanel: React.FC<TaskConfigPanelProps> = ({taskDetail, taskConfig, groupOptions}) => {
    const {t} = useTranslation();
    if (!taskDetail || !taskConfig) return null;
    const mainTask = taskConfig;

    const groupLabel = mainTask.group
        ? (groupOptions.find(opt => opt.value === mainTask.group)?.label || mainTask.group)
        : '-';

    const authTypeLabel =
        mainTask.authType === 'none' ? t('task.form.noAuth') :
        mainTask.authType === 'normal' ? t('task.form.normalAuth') :
            mainTask.authType === 'byoc' ? 'BYOC' :
                mainTask.authType === 'iotCore' ? 'IoT Core' :
                    mainTask.authType || t('task.form.noAuth');

    const taskTypeLabel = mainTask.taskType === 'CONN' ? t('task.type.CONN') :
        mainTask.taskType === 'CHAOS' ? t('task.type.CHAOS') :
            t('task.type.PUBSUB');
    const protocol = (mainTask.protocol || 'mqtt').toUpperCase();
    const brokerGroups = Array.from(new Set((taskDetail.brokers || []).map(b => b.group).filter(Boolean)));
    const brokerGroupLabel = brokerGroups.length > 0
        ? brokerGroups
            .map(group => groupOptions.find(opt => opt.value === group)?.label || group)
            .join(', ')
        : '-';
    const brokerList = (taskDetail.brokers || []).map(b => `${b.name || b.brokerId || b.host}:${b.port}`).join(', ') || '-';
    const certEnabled = !!mainTask.clientCertId;

    return (
        <div style={{padding: '4px 0'}}>
            <StatusBar taskDetail={taskDetail} taskConfig={mainTask}/>

            <div style={{marginBottom: 16}}>
                <SectionTitle label={t('task.form.basicSettings')} color="#1677ff"/>
                <Descriptions bordered column={2} size="small">
                    <Descriptions.Item label={t('task.form.taskName')}>{taskDetail.taskName || '-'}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.taskType')}>{taskTypeLabel}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.taskGroup')}>{groupLabel}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.taskTemplate')}>{mainTask.template || '-'}</Descriptions.Item>
                    <Descriptions.Item label={t('common.createdAt')} span={2}>
                        {formatDateTime(taskDetail.createTime)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('task.detail.basicInfo.taskId')} span={2}>
                        <span style={{fontFamily: 'monospace', fontSize: 12}}>{taskDetail.taskId}</span>
                    </Descriptions.Item>
                </Descriptions>
            </div>

            <div style={{marginBottom: 16}}>
                <SectionTitle label={t('task.form.connConfig')} color="#52c41a"/>
                <Descriptions bordered column={2} size="small">
                    <Descriptions.Item label={t('task.form.protocolType')}>{protocol}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.authType')}>{authTypeLabel}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.enableCert')}>{certEnabled ? t('common.yes') : t('common.no')}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.clientCert')}>{mainTask.clientCertId || '-'}</Descriptions.Item>
                    {mainTask.authType === 'normal' && (
                        <>
                            <Descriptions.Item label={t('task.form.username')}>{mainTask.username || '-'}</Descriptions.Item>
                            <Descriptions.Item label={t('task.form.password')}>{mainTask.password ? '***' : '-'}</Descriptions.Item>
                        </>
                    )}
                    {mainTask.authType === 'byoc' && (
                        <>
                            <Descriptions.Item label={t('task.form.tenantId')}>{mainTask.tenantId || '-'}</Descriptions.Item>
                            <Descriptions.Item label={t('task.form.thingIdPrefix')}>{mainTask.thingIdPrefix || '-'}</Descriptions.Item>
                            <Descriptions.Item label={t('task.form.thingIdStartAt')}>{mainTask.thingIdStartAt ?? 0}</Descriptions.Item>
                        </>
                    )}
                </Descriptions>
            </div>

            <div style={{marginBottom: 16}}>
                <SectionTitle label={t('task.form.brokerConfig')} color="#722ed1"/>
                <Descriptions bordered column={1} size="small">
                    <Descriptions.Item label={t('task.form.brokerGroup')}>
                        {brokerGroupLabel}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('task.form.selectBroker')}>
                        {brokerList}
                    </Descriptions.Item>
                </Descriptions>
            </div>
        </div>
    );
};

// ─── Tab 2: Stress params (corresponds to second Tab in create modal) ──────────────────────────────
export const StressParamsPanel: React.FC<{ taskDetail: TaskDetailResponse | null; taskConfig: TaskConfig | null }> = ({
                                                                                                                        taskDetail,
                                                                                                                        taskConfig,
                                                                                                                    }) => {
    const {t} = useTranslation();
    if (!taskDetail || !taskConfig) return null;
    const mainTask = taskConfig;

    const isChaos = mainTask.taskType === 'CHAOS';
    const includePub = hasPub(mainTask.template);
    const includeSub = hasSub(mainTask.template);
    const connectQpsMode = mainTask.connectProfileId ? 'DYNAMIC' : 'FIXED';
    const disconnectQpsMode = mainTask.disconnectProfileId ? 'DYNAMIC' : 'FIXED';
    const publishQpsMode = mainTask.qpsMode === 'DYNAMIC' && mainTask.profileConfig?.profileId
        ? 'DYNAMIC' : 'FIXED';
    const publishProfileLabel = taskDetail.publishProfile?.name
        || mainTask.profileConfig?.profileName
        || mainTask.profileConfig?.profileId
        || '-';
    const subscribeQpsMode = mainTask.subscribeQpsMode || (mainTask.subscribeProfileId ? 'DYNAMIC' : 'FIXED');
    const fanMode = (mainTask.fanOut || 1) > 1 ? 'fanOut' : (mainTask.fanIn || 1) > 1 ? 'fanIn' : 'default';
    const payloadModeLabel = mainTask.payloadMode === 'TEMPLATE' ? t('task.form.payloadModeTemplate') :
        mainTask.payloadMode === 'RANDOM' ? t('task.form.payloadModeRandom') :
            t('task.form.payloadModeBifro');
    const qpsModeLabel = (mode: string) => mode === 'DYNAMIC' ? t('task.form.dynamicProfile') : t('task.form.fixedRate');
    const topicPattern = generatedTopicPattern(mainTask);
    const topicOffset = topicOffsetPattern(mainTask);
    const showTopicsPerClient = hasTopicsPerClient(mainTask.template);
    const topicsPerClient = showTopicsPerClient ? (mainTask.topicsPerClient || 1) : 1;

    return (
        <div style={{padding: '4px 0'}}>
            <StatusBar taskDetail={taskDetail} taskConfig={mainTask}/>

            <div style={{marginBottom: 16}}>
                <SectionTitle label={t('task.form.clientConfig')} color="#1677ff"/>
                <Descriptions bordered column={2} size="small">
                    <Descriptions.Item label={t('task.form.connectQpsMode')}>{qpsModeLabel(connectQpsMode)}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.disconnectQpsMode')}>{qpsModeLabel(disconnectQpsMode)}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.connectRatePerSec')}>{mainTask.connectRate || 100}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.disconnectRatePerSec')}>{mainTask.disconnectRate || 2000}</Descriptions.Item>
                    {connectQpsMode === 'DYNAMIC' && (
                        <Descriptions.Item label={t('task.form.connectProfile')}>{mainTask.connectProfileId || '-'}</Descriptions.Item>
                    )}
                    {disconnectQpsMode === 'DYNAMIC' && (
                        <Descriptions.Item label={t('task.form.disconnectProfile')}>{mainTask.disconnectProfileId || '-'}</Descriptions.Item>
                    )}
                    <Descriptions.Item
                        label={t('task.form.clientCount')}>{mainTask.totalClientCount?.toLocaleString() || '0'}</Descriptions.Item>
                </Descriptions>
            </div>

            <div style={{marginBottom: 16}}>
                <SectionTitle label={t('task.form.timingParams')} color="#52c41a"/>
                <Descriptions bordered column={2} size="small">
                    <Descriptions.Item label={t('task.form.stressDuration')}>{mainTask.stressDurationInSec || 60} {t('common.unit.seconds')}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.stageTimeout')}>{mainTask.stageTimeoutInSec || 30} {t('common.unit.seconds')}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.delayAfterStage')}>{mainTask.delayAfterStageInSec ?? 30} {t('common.unit.seconds')}</Descriptions.Item>
                </Descriptions>
            </div>

            {includePub && (
                <div style={{marginBottom: 16}}>
                    <SectionTitle label={t('task.form.publishParams')} color="#fa8c16"/>
                    <Descriptions bordered column={2} size="small">
                        <Descriptions.Item label={t('task.form.pubTopic')} span={2}>{topicPattern || '-'}</Descriptions.Item>
                        {showTopicsPerClient && (
                            <Descriptions.Item label={t('task.form.topicsPerClient')}>{topicsPerClient}</Descriptions.Item>
                        )}
                        {topicOffset && (
                            <Descriptions.Item label="Topic Offset">{topicOffset}</Descriptions.Item>
                        )}
                        <Descriptions.Item label={t('task.form.messageSize')}>{mainTask.messageSize || 32} {t('common.unit.bytes')}</Descriptions.Item>
                        <Descriptions.Item label={t('task.form.publishQpsMode')}>{qpsModeLabel(publishQpsMode)}</Descriptions.Item>
                        {publishQpsMode === 'DYNAMIC' ? (
                            <Descriptions.Item label={t('task.form.publishProfile')}>
                                {publishProfileLabel}
                            </Descriptions.Item>
                        ) : (
                            <Descriptions.Item label={t('task.form.publishRate')}>
                                {mainTask.publishRate ?? 1} QPS
                            </Descriptions.Item>
                        )}
                        <Descriptions.Item label={t('task.form.payloadMode')}>{payloadModeLabel}</Descriptions.Item>
                        <Descriptions.Item label={t('task.form.qos')}>{mainTask.qos ?? 0}</Descriptions.Item>
                        {mainTask.payloadMode === 'TEMPLATE' && mainTask.payloadTemplate && (
                            <Descriptions.Item label={t('task.form.msgTemplate')} span={2}>
                                <span style={{fontFamily: 'monospace', fontSize: 12}}>{mainTask.payloadTemplate}</span>
                            </Descriptions.Item>
                        )}
                    </Descriptions>
                </div>
            )}

            {includeSub && (
                <div style={{marginBottom: 16}}>
                    <SectionTitle label={t('task.form.subscribeParams')} color="#13c2c2"/>
                    <Descriptions bordered column={2} size="small">
                        <Descriptions.Item label={t('task.form.subTopic')} span={2}>{topicPattern || '-'}</Descriptions.Item>
                        {showTopicsPerClient && (
                            <Descriptions.Item label={t('task.form.topicsPerClient')}>{topicsPerClient}</Descriptions.Item>
                        )}
                        {topicOffset && (
                            <Descriptions.Item label="Topic Offset">{topicOffset}</Descriptions.Item>
                        )}
                        <Descriptions.Item label={t('task.form.qos')}>{mainTask.qos ?? 0}</Descriptions.Item>
                        <Descriptions.Item label={t('task.form.subscribeQpsMode')}>{qpsModeLabel(subscribeQpsMode)}</Descriptions.Item>
                        <Descriptions.Item label={t('task.form.subscribeRatePerSec')}>{mainTask.subscribeRate ?? 0}</Descriptions.Item>
                        {subscribeQpsMode === 'DYNAMIC' && (
                            <Descriptions.Item label={t('task.form.subscribeProfile')}>{mainTask.subscribeProfileId || '-'}</Descriptions.Item>
                        )}
                        <Descriptions.Item label={t('task.form.fanMode')}>
                            {fanMode === 'fanOut' ? t('task.form.fanModeOut') : fanMode === 'fanIn' ? t('task.form.fanModeIn') : t('task.form.fanModeDefault')}
                        </Descriptions.Item>
                        {fanMode === 'fanOut' && (
                            <Descriptions.Item label={t('task.form.fanOutMultiplier')}>{mainTask.fanOut || 2}</Descriptions.Item>
                        )}
                        {fanMode === 'fanIn' && (
                            <Descriptions.Item label={t('task.form.fanInMultiplier')}>{mainTask.fanIn || 2}</Descriptions.Item>
                        )}
                    </Descriptions>
                </div>
            )}

            {isChaos && (
                <div style={{marginBottom: 16}}>
                    <SectionTitle label={t('task.form.chaosParams')} color="#f5222d"/>
                    <Descriptions bordered column={2} size="small">
                        <Descriptions.Item label={t('task.form.chaosBehaviors')} span={2}>
                            {mainTask.chaosPolicy?.behaviors?.join(', ') || '-'}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('task.form.chaosRatio')}>
                            {((mainTask.chaosPolicy?.targetRatio ?? 1) * 100).toFixed(0)} %
                        </Descriptions.Item>
                        <Descriptions.Item label={t('task.form.maxInflight')}>
                            {mainTask.chaosPolicy?.maxInflight ?? '-'}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('task.form.maxPacketSize')}>
                            {mainTask.chaosPolicy?.maxPacketSizeOverride ?? 0} {t('common.unit.bytes')}
                        </Descriptions.Item>
                    </Descriptions>
                </div>
            )}
        </div>
    );
};

// ─── Tab 3: Advanced config (corresponds to third Tab in create modal) ──────────────────────────────
export const AdvancedConfigPanel: React.FC<{ taskDetail: TaskDetailResponse | null; taskConfig: TaskConfig | null }> = ({
                                                                                                                          taskDetail,
                                                                                                                          taskConfig,
                                                                                                                      }) => {
    const {t} = useTranslation();
    if (!taskDetail || !taskConfig) return null;
    const mainTask = taskConfig;

    const autoMultiAddress = (mainTask.enableAutoMultiAddress ?? (mainTask as any).autoMultiAddress ?? true) as boolean;
    const mqtt5 = (mainTask.mqtt5 ?? mainTask.isMqtt5 ?? false) as boolean;
    const emptyClientId = (mainTask.isEmptyClientId ?? (mainTask as any).emptyClientId ?? false) as boolean;

    return (
        <div style={{padding: '4px 0'}}>
            <StatusBar taskDetail={taskDetail} taskConfig={mainTask}/>

            <div style={{marginBottom: 16}}>
                <SectionTitle label={t('task.form.protocolOptions')} color="#1677ff"/>
                <Descriptions bordered column={2} size="small">
                    <Descriptions.Item
                        label={t('task.form.cleanSession')}>{mainTask.cleanSession !== false ? t('common.yes') : t('common.no')}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.mqtt5')}>{mqtt5 ? t('common.yes') : t('common.no')}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.autoMultiAddress')}>{autoMultiAddress ? t('common.yes') : t('common.no')}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.emptyClientId')}>{emptyClientId ? t('common.yes') : t('common.no')}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.retain')}>{mainTask.retain ? t('common.yes') : t('common.no')}</Descriptions.Item>
                </Descriptions>
            </div>

            <div style={{marginBottom: 16}}>
                <SectionTitle label={t('task.form.connParams')} color="#52c41a"/>
                <Descriptions bordered column={2} size="small">
                    <Descriptions.Item label={t('task.form.keepAlive')}>{mainTask.keepAliveInSec || 120} {t('common.unit.seconds')}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.expiryInterval')}>{mainTask.expiryIntervalInSec || 120} {t('common.unit.seconds')}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.maxQueueSize')}>{mainTask.maxInflightQueue || 200}</Descriptions.Item>
                </Descriptions>
            </div>

            <div style={{marginBottom: 16}}>
                <SectionTitle label={t('task.form.timeoutAndReconnect')} color="#fa8c16"/>
                <Descriptions bordered column={2} size="small">
                    <Descriptions.Item label={t('task.form.connectTimeout')}>{mainTask.connectTimeoutInMs || 10000} {t('common.unit.ms')}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.ackTimeout')}>{mainTask.ackTimeoutInSec || 120} {t('common.unit.seconds')}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.reconnectMaxAttempts')}>{mainTask.reconnectMaxAttempts || 2}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.reconnectInterval')}>{mainTask.reconnectIntervalInMs || 5000} {t('common.unit.ms')}</Descriptions.Item>
                </Descriptions>
            </div>

            <div style={{marginBottom: 16}}>
                <SectionTitle label={t('task.form.willConfig')} color="#eb2f96"/>
                <Descriptions bordered column={2} size="small">
                    <Descriptions.Item
                        label={t('task.form.enableWill')}>{mainTask.willConfig?.willFlag ? t('common.yes') : t('common.no')}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.willTopic')}>{mainTask.willConfig?.willTopic || '-'}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.willMessage')}>{mainTask.willConfig?.willMessage || '-'}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.willQos')}>{mainTask.willConfig?.willQos ?? 0}</Descriptions.Item>
                    <Descriptions.Item label={t('task.form.willRetain')}>{mainTask.willConfig?.willRetain ? t('common.yes') : t('common.no')}</Descriptions.Item>
                </Descriptions>
            </div>
        </div>
    );
};

// ─── Default export (kept for compatibility, no longer in use) ───────────────────────────────────
const TaskBasicInfoSection: React.FC<TaskBasicInfoSectionProps> = (props) => {
    if (!props.taskDetail || !props.taskConfig) return null;
    return (
        <>
            <TaskConfigPanel {...props} />
            {props.taskDetail && !props.taskDetail.success && (
                <Alert description={props.taskDetail.message} type="error" showIcon style={{marginTop: 16}}/>
            )}
        </>
    );
};

export default TaskBasicInfoSection;
