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
import {Alert, Form, message, Modal, Tabs} from 'antd';
import {useNavigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {useTaskData, useTaskEditorData} from '../../features/task/model';
import TaskConfigForm from './components/TaskEditor/TaskConfigForm';
import StressParamsForm from './components/TaskEditor/StressParamsForm';
import AdvancedConfigForm from './components/TaskEditor/AdvancedConfigForm';
import type {TaskListItem, TaskRequest} from '../../features/task';
import {TaskTemplateValues, TaskTypeValues} from '../../features/task';
import type {WaveformProfile} from '../../features/profile';
import {listProfiles} from '../../features/profile';

interface TaskEditorProps {
    visible: boolean;
    editingTask: TaskListItem | null;
    initialGroup?: string;
    onCancel: () => void;
    onOk: (taskId: string | undefined, taskRequest: TaskRequest) => Promise<void>;
}

// Infer pubSubMode from template
const getPubSubModeFromTemplate = (template: string): string => {
    if (template === TaskTemplateValues.PUBSUB_PUB_ONLY) return 'pubOnly';
    if (template === TaskTemplateValues.PUBSUB_SUB_ONLY) return 'subOnly';
    return 'pubsub';
};

const brokerSelectValue = (broker: { brokerId?: string; host: string; port: number }) =>
    broker.brokerId || `${broker.host}:${broker.port}`;

const resolveBrokerGroup = (brokers?: Array<{ group?: string }>, fallbackGroup: string = '') =>
    brokers?.find(broker => broker.group)?.group || fallbackGroup;

const toBrokerRequest = (brokerId: string, availableBrokers: any[]) => {
    const found = availableBrokers.find((b: any) => b.brokerId === brokerId);
    if (found) {
        return {brokerId: found.brokerId, host: found.host, port: found.port};
    }
    const lastColonIndex = brokerId.lastIndexOf(':');
    if (lastColonIndex > 0) {
        const host = brokerId.slice(0, lastColonIndex);
        const port = Number(brokerId.slice(lastColonIndex + 1));
        if (Number.isInteger(port) && port > 0 && port <= 65535) {
            return {brokerId, host, port};
        }
    }
    return {brokerId, host: brokerId, port: 1883};
};

const TaskEditor: React.FC<TaskEditorProps> = ({
                                                   visible,
                                                   editingTask,
                                                   initialGroup,
                                                   onCancel,
                                                   onOk
                                               }) => {
    const navigate = useNavigate();
    const {t} = useTranslation();
    const [form] = Form.useForm();
    const [currentTaskType, setCurrentTaskType] = useState<string>(TaskTypeValues.CONN);
    const [currentTemplate, setCurrentTemplate] = useState<string>(TaskTemplateValues.CONN_STANDARD);
    const [willEnabled, setWillEnabled] = useState<boolean>(false);
    const [authType, setAuthType] = useState<string>('none');
    const [certEnabled, setCertEnabled] = useState<boolean>(false);
    const [fanMode, setFanMode] = useState<'default' | 'fanOut' | 'fanIn'>('default');
    const {loadTaskConfig, loadBrokers} = useTaskData();
    const {
        templateOptions,
        clientCertOptions,
        brokerGroupSelectOptions,
        loadTemplateOptions,
        loadClientCertOptions,
        loadBrokerGroupSelectOptions,
    } = useTaskEditorData();
    const [brokers, setBrokers] = useState<any[]>([]);
    const [brokerLoading, setBrokerLoading] = useState(false);
    const [selectedBrokerGroup, setSelectedBrokerGroup] = useState<string>('');
    const [profiles, setProfiles] = useState<WaveformProfile[]>([]);
    const hasBrokerGroups = brokerGroupSelectOptions.length > 0;
    // Cache chaosPolicy for delayed injection after CHAOS taskType renders (conditional Form.Item rendering makes early setFieldsValue ineffective)
    const [pendingChaosPolicy, setPendingChaosPolicy] = useState<any>(null);
    const [activeTabKey, setActiveTabKey] = useState('config');

    // When taskType switches to CHAOS, CHAOS Form.Item mounts; inject chaosPolicy at this point
    useEffect(() => {
        if (currentTaskType === TaskTypeValues.CHAOS && pendingChaosPolicy) {
            form.setFieldsValue({chaosPolicy: pendingChaosPolicy});
            setPendingChaosPolicy(null);
        }
    }, [currentTaskType, pendingChaosPolicy, form]);

    const loadBrokerList = async (groupId: string = selectedBrokerGroup) => {
        setBrokerLoading(true);
        try {
            const brokerData = await loadBrokers();
            setBrokers(groupId ? brokerData.filter((b: any) => b.group === groupId) : brokerData);
        } catch (error) {
            console.error('Failed to load brokers:', error);
        } finally {
            setBrokerLoading(false);
        }
    };

    const loadTaskData = async (id: string) => {
        try {
            const mainTask = await loadTaskConfig(id);
            if (mainTask) {
                const template = mainTask.template;
                const resolvedTemplate = template ||
                    (mainTask.taskType === TaskTypeValues.CONN
                        ? TaskTemplateValues.CONN_STANDARD
                        : mainTask.taskType === TaskTypeValues.CHAOS
                            ? TaskTemplateValues.CHAOS_STANDARD
                            : TaskTemplateValues.PUBSUB_STANDARD);
                const pubSubMode = getPubSubModeFromTemplate(resolvedTemplate);
                let fanModeValue: 'default' | 'fanOut' | 'fanIn' = 'default';
                if ((mainTask.fanIn ?? 1) > 1) fanModeValue = 'fanIn';
                else if ((mainTask.fanOut ?? 1) > 1) fanModeValue = 'fanOut';
                setFanMode(fanModeValue);
                const taskGroup = mainTask.group || editingTask?.group || '';

                form.setFieldsValue({
                    ...mainTask,
                    taskName: editingTask?.taskName,
                    group: taskGroup,
                    template: resolvedTemplate,
                    brokers: mainTask.brokers?.map(brokerSelectValue) || [],
                    clientCertId: mainTask.clientCertId || undefined,
                    willConfig: mainTask.willConfig || {willFlag: false},
                    autoMultiAddress: mainTask.enableAutoMultiAddress || false,
                    mqtt5: mainTask.isMqtt5 ?? mainTask.mqtt5 ?? false,
                    emptyClientId: mainTask.isEmptyClientId ?? false,
                    pubSubMode,
                    fanMode: fanModeValue,
                    // Infer QPS mode from profileId presence (frontend-only field)
                    connectQpsMode: mainTask.connectProfileId ? 'DYNAMIC' : 'FIXED',
                    disconnectQpsMode: mainTask.disconnectProfileId ? 'DYNAMIC' : 'FIXED',
                    publishQpsMode: mainTask.qpsMode === 'DYNAMIC' && mainTask.profileConfig?.profileId
                        ? 'DYNAMIC' : 'FIXED',
                    publishProfileId: mainTask.profileConfig?.profileId,
                    subscribeQpsMode: mainTask.subscribeQpsMode ?? 'FIXED',
                });
                setCurrentTaskType(mainTask.taskType);
                setCurrentTemplate(resolvedTemplate);
                setWillEnabled(mainTask.willConfig?.willFlag || false);
                setAuthType(mainTask.authType || 'none');
                setCertEnabled(!!mainTask.clientCertId);
                // For CHAOS tasks, chaosPolicy must be injected after CHAOS Form.Item mounts
                if (mainTask.taskType === TaskTypeValues.CHAOS && mainTask.chaosPolicy) {
                    setPendingChaosPolicy(mainTask.chaosPolicy);
                }
                const brokerGroup = resolveBrokerGroup(mainTask.brokers, taskGroup);
                setSelectedBrokerGroup(brokerGroup);
                loadBrokerList(brokerGroup);
            }
        } catch (error) {
            console.error('Failed to load task detail:', error);
        }
    };

    useEffect(() => {
        if (visible) {
            const initForm = async () => {
                const defaultBrokerGroupId = await loadBrokerGroupSelectOptions();
                loadTemplateOptions();
                listProfiles().then(setProfiles).catch(() => setProfiles([]));

                if (!editingTask && !defaultBrokerGroupId && !initialGroup) {
                    form.resetFields();
                    setSelectedBrokerGroup(defaultBrokerGroupId || '');
                    loadBrokerList(defaultBrokerGroupId || '');
                    loadClientCertOptions();
                    return;
                }

                if (editingTask) {
                    if (editingTask.id === '' && editingTask.taskConfig) {
                        const mainTask = editingTask.taskConfig;
                        const copyTaskType = mainTask.taskType === TaskTypeValues.CHAOS
                            ? TaskTypeValues.CONN : mainTask.taskType;
                        const template = mainTask.taskType === TaskTypeValues.CHAOS
                            ? TaskTemplateValues.CONN_STANDARD : mainTask.template;
                        const resolvedTemplate = template ||
                            (copyTaskType === TaskTypeValues.CONN
                                ? TaskTemplateValues.CONN_STANDARD
                                : TaskTemplateValues.PUBSUB_STANDARD);
                        const pubSubMode = getPubSubModeFromTemplate(resolvedTemplate);
                        let fanModeValue: 'default' | 'fanOut' | 'fanIn' = 'default';
                        if ((mainTask.fanIn ?? 1) > 1) fanModeValue = 'fanIn';
                        else if ((mainTask.fanOut ?? 1) > 1) fanModeValue = 'fanOut';
                        setFanMode(fanModeValue);
                        const taskGroup = editingTask.group || mainTask.group || initialGroup || defaultBrokerGroupId || '';

                        form.setFieldsValue({
                            ...mainTask,
                            taskName: editingTask.taskName,
                            taskType: copyTaskType,
                            group: taskGroup,
                            template: resolvedTemplate,
                            brokers: editingTask.brokers?.map(brokerSelectValue) || [],
                            clientCertId: mainTask.clientCertId || undefined,
                            willConfig: mainTask.willConfig || {willFlag: false},
                            autoMultiAddress: mainTask.enableAutoMultiAddress || false,
                            mqtt5: mainTask.isMqtt5 ?? mainTask.mqtt5 ?? false,
                            emptyClientId: mainTask.isEmptyClientId || false,
                            pubSubMode,
                            fanMode: fanModeValue,
                            connectQpsMode: mainTask.connectProfileId ? 'DYNAMIC' : 'FIXED',
                            disconnectQpsMode: mainTask.disconnectProfileId ? 'DYNAMIC' : 'FIXED',
                            publishQpsMode: mainTask.qpsMode === 'DYNAMIC' && mainTask.profileConfig?.profileId
                                ? 'DYNAMIC' : 'FIXED',
                            publishProfileId: mainTask.profileConfig?.profileId,
                            subscribeQpsMode: mainTask.subscribeQpsMode ?? 'FIXED',
                        });
                        setCurrentTaskType(copyTaskType);
                        setCurrentTemplate(resolvedTemplate);
                        setWillEnabled(mainTask.willConfig?.willFlag || false);
                        setAuthType(mainTask.authType || 'none');
                        setCertEnabled(!!mainTask.clientCertId);
                        const brokerGroup = resolveBrokerGroup(editingTask.brokers, taskGroup || defaultBrokerGroupId || '');
                        setSelectedBrokerGroup(brokerGroup);
                        loadBrokerList(brokerGroup);
                    } else {
                        loadTaskData(editingTask.id);
                    }
                } else {
                    // Create mode
                    form.resetFields();
                    form.setFieldsValue({
                        template: TaskTemplateValues.CONN_STANDARD,
                        group: initialGroup || defaultBrokerGroupId || '',
                    });
                    setCurrentTaskType(TaskTypeValues.CONN);
                    setCurrentTemplate(TaskTemplateValues.CONN_STANDARD);
                    setWillEnabled(false);
                    setAuthType('none');
                    setCertEnabled(false);
                    setFanMode('default');
                    setSelectedBrokerGroup(defaultBrokerGroupId || '');
                    loadBrokerList(defaultBrokerGroupId || '');
                }
                loadClientCertOptions();
            };
            initForm();
        }
    }, [visible, editingTask]);

    // Field → Tab mapping (auto navigate on validation failure)
    const fieldToTabKey: Record<string, string> = {
        taskName: 'config', taskType: 'config', template: 'config', group: 'config',
        protocol: 'config', authType: 'config', username: 'config', password: 'config',
        tenantId: 'config', thingIdPrefix: 'config', thingIdStartAt: 'config',
        brokers: 'config', clientCertEnabled: 'config', clientCertId: 'config',
        topic: 'stress', qos: 'stress', topicsPerClient: 'stress',
        totalClientCount: 'stress', connectRate: 'stress', disconnectRate: 'stress',
        stressDurationInSec: 'stress', stageTimeoutInSec: 'stress', delayAfterStageInSec: 'stress',
        messageSize: 'stress', publishQpsMode: 'stress', publishProfileId: 'stress',
        publishRate: 'stress', pubSubMode: 'stress',
        fanMode: 'stress', fanOut: 'stress', fanIn: 'stress',
        cleanSession: 'advanced', autoMultiAddress: 'advanced',
        mqtt5: 'advanced', isEmptyClientId: 'advanced', retain: 'stress', fixedTopic: 'advanced',
        keepAliveInSec: 'advanced', expiryIntervalInSec: 'advanced', maxInflightQueue: 'advanced',
        connectTimeoutInMs: 'advanced', ackTimeoutInSec: 'advanced',
        reconnectMaxAttempts: 'advanced', reconnectIntervalInMs: 'advanced',
    };

    const handleOk = async () => {
        try {
            await form.validateFields();
            const values = form.getFieldsValue(true);
            const selectedBrokerIds: string[] = values.brokers || [];
            const brokerItems = selectedBrokerIds.map((brokerId: string) => toBrokerRequest(brokerId, brokers));
            const publishProfile = profiles.find(p => p.id === values.publishProfileId);
            const stressDurationInSec = values.publishQpsMode === 'DYNAMIC' && publishProfile
                ? Math.max(1, Math.ceil(publishProfile.totalDurationMs / 1000))
                : (values.stressDurationInSec ?? 60);

            const taskRequest: TaskRequest = {
                taskName: values.taskName,
                taskType: values.taskType,
                template: values.template,
                group: values.group ?? '',
                autoMultiAddress: values.autoMultiAddress ?? false,
                brokers: brokerItems,
                cleanSession: values.cleanSession !== false,
                totalClientCount: values.connectQpsMode === 'DYNAMIC'
                    ? (profiles.find(p => p.id === values.connectProfileId)?.integral ?? values.totalClientCount ?? 100)
                    : (values.totalClientCount ?? 100),
                connectRate: values.connectRate ?? 100,
                disconnectRate: values.disconnectRate ?? 2000,
                fanOut: values.fanOut ?? 1,
                fanIn: values.fanIn ?? 1,
                topicsPerClient: values.topicsPerClient ?? 1,
                qos: values.qos ?? 0,
                topic: values.topic ?? null,
                fixedTopic: values.fixedTopic ?? false,
                messageSize: values.messageSize ?? 32,
                publishRate: values.publishRate ?? 1,
                stressDurationInSec,
                stageTimeoutInSec: values.stageTimeoutInSec ?? 30,
                delayAfterStageInSec: values.delayAfterStageInSec ?? 30,
                retain: values.retain ?? false,
                authType: values.authType ?? 'normal',
                expiryIntervalInSec: values.expiryIntervalInSec ?? 120,
                willConfig: values.willConfig ?? {willFlag: false},
                thingIdStartAt: values.thingIdStartAt ?? 0,
                thingIdPrefix: values.thingIdPrefix ?? null,
                protocol: values.protocol ?? 'mqtt',
                username: values.username ?? '',
                password: values.password ?? '',
                tenantId: values.tenantId ?? null,
                keepAliveInSec: values.keepAliveInSec ?? 120,
                ackTimeoutInSec: values.ackTimeoutInSec ?? 120,
                reconnectMaxAttempts: values.reconnectMaxAttempts ?? 2,
                reconnectIntervalInMs: values.reconnectIntervalInMs ?? 5000,
                connectTimeoutInMs: values.connectTimeoutInMs ?? 10000,
                maxInflightQueue: values.maxInflightQueue ?? 200,
                mqtt5: values.mqtt5 ?? false,
                isMqtt5: values.mqtt5 ?? false,
                emptyClientId: values.emptyClientId ?? false,
                isEmptyClientId: values.emptyClientId ?? false,
                clientCertEnabled: values.clientCertEnabled ?? false,
                clientCertId: values.clientCertEnabled ? (values.clientCertId ?? undefined) : undefined,
                payloadMode: values.payloadMode ?? 'BIFRO',
                payloadTemplate: values.payloadMode === 'TEMPLATE' ? (values.payloadTemplate ?? undefined) : undefined,
                qpsMode: values.publishQpsMode === 'DYNAMIC' ? 'DYNAMIC' : 'FIXED',
                profileConfig: values.publishQpsMode === 'DYNAMIC' ? {
                    profileId: values.publishProfileId,
                    profileName: publishProfile?.name,
                    integral: publishProfile?.integral,
                } : undefined,
                waveQpsSpec: undefined,
                connectWaveQpsSpec: undefined,
                disconnectWaveQpsSpec: undefined,
                // connect / disconnect profile
                connectProfileId: values.connectQpsMode === 'DYNAMIC' ? (values.connectProfileId ?? undefined) : undefined,
                disconnectProfileId: values.disconnectQpsMode === 'DYNAMIC' ? (values.disconnectProfileId ?? undefined) : undefined,
                // subscribe QPS
                subscribeQpsMode: values.subscribeQpsMode ?? 'FIXED',
                subscribeRate: values.subscribeQpsMode !== 'DYNAMIC' ? (values.subscribeRate ?? 0) : undefined,
                subscribeProfileId: values.subscribeQpsMode === 'DYNAMIC' ? (values.subscribeProfileId ?? undefined) : undefined,
                chaosPolicy: values.taskType === 'CHAOS' ? (values.chaosPolicy ?? undefined) : undefined,
            };

            await onOk(editingTask?.taskId, taskRequest);
            form.resetFields();
        } catch (error: any) {
            console.error('Form validation failed:', error);
            if (error?.errorFields?.length > 0) {
                const firstError = error.errorFields[0];
                message.error(firstError.errors?.[0] || t('task.msg.fillRequired'));
                const fieldName = firstError.name?.[0] as string;
                const targetTab = fieldToTabKey[fieldName];
                if (targetTab) setActiveTabKey(targetTab);
                setTimeout(() => form.scrollToField(firstError.name), 0);
            }
        }
    };

    const handleCancel = () => {
        form.resetFields();
        setCurrentTaskType(TaskTypeValues.CONN);
        setCurrentTemplate(TaskTemplateValues.CONN_STANDARD);
        setWillEnabled(false);
        setAuthType('none');
        setCertEnabled(false);
        setFanMode('default');
        setSelectedBrokerGroup('');
        setPendingChaosPolicy(null);
        onCancel();
    };

    const tabItems = [
        {
            key: 'config',
            label: t('task.editor.tabs.config'),
            children: (
                <TaskConfigForm
                    currentTaskType={currentTaskType}
                    templateOptions={templateOptions}
                    initialGroup={initialGroup}
                    authType={authType}
                    certEnabled={certEnabled}
                    brokers={brokers}
                    brokerLoading={brokerLoading}
                    brokerGroupSelectOptions={brokerGroupSelectOptions}
                    clientCertOptions={clientCertOptions}
                    selectedBrokerGroup={selectedBrokerGroup}
                    onAuthTypeChange={setAuthType}
                    onCertEnabledChange={setCertEnabled}
                    onBrokerGroupChange={(value) => {
                        setSelectedBrokerGroup(value ?? '');
                        form.setFieldsValue({brokers: []});
                        loadBrokerList(value);
                    }}
                    onNavigateToBrokerGroups={() => navigate('/mqtt-instances?tab=groups')}
                    onNavigateToCertificates={() => navigate('/certificates')}
                />
            ),
        },
        {
            key: 'stress',
            label: t('task.editor.tabs.stressParams'),
            children: (
                <StressParamsForm
                    form={form}
                    currentTemplate={currentTemplate}
                    fanMode={fanMode}
                    onFanModeChange={setFanMode}
                    profiles={profiles}
                />
            ),
        },
        {
            key: 'advanced',
            label: t('task.editor.tabs.advancedConfig'),
            children: (
                <AdvancedConfigForm
                    willEnabled={willEnabled}
                    onWillEnabledChange={setWillEnabled}
                />
            ),
        },
    ];

    return (
        <Modal
            title={editingTask ? t('task.editor.editTitle') : t('task.editor.addTitle')}
            open={visible}
            onOk={handleOk}
            okButtonProps={{disabled: !hasBrokerGroups}}
            onCancel={handleCancel}
            width={900}
            styles={{body: {maxHeight: '72vh', overflowY: 'auto', padding: '8px 24px 16px'}}}
        >
            {!hasBrokerGroups && (
                <Alert
                    type="warning"
                    showIcon
                    message={t('task.msg.noBrokerGroups')}
                    description={t('task.msg.createBrokerGroupFirst')}
                    style={{marginBottom: 16}}
                />
            )}
            <Form
                form={form}
                layout="vertical"
                onValuesChange={(changedValues) => {
                    if ('taskType' in changedValues) {
                        const newType = changedValues.taskType;
                        setCurrentTaskType(newType);
                        const defaultTemplate = newType === TaskTypeValues.CONN
                            ? TaskTemplateValues.CONN_STANDARD
                            : TaskTemplateValues.PUBSUB_STANDARD;
                        setCurrentTemplate(defaultTemplate);
                        form.setFieldsValue({template: defaultTemplate});
                    }
                    if ('template' in changedValues) {
                        const newTemplate = changedValues.template;
                        setCurrentTemplate(newTemplate);
                        if (typeof newTemplate === 'string' && newTemplate.startsWith('PUBSUB')) {
                            form.setFieldsValue({pubSubMode: getPubSubModeFromTemplate(newTemplate)});
                        }
                    }
                    if ('authType' in changedValues) setAuthType(changedValues.authType);
                    if ('clientCertEnabled' in changedValues) setCertEnabled(changedValues.clientCertEnabled);
                    if ('willConfig' in changedValues && changedValues.willConfig?.willFlag !== undefined) {
                        setWillEnabled(changedValues.willConfig.willFlag);
                    }
                }}
            >
                <Tabs activeKey={activeTabKey} onChange={setActiveTabKey} size="small" items={tabItems}/>
            </Form>
        </Modal>
    );
};

export default TaskEditor;
