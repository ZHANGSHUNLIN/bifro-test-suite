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
import {Button, Col, Divider, Form, Input, Row, Select} from 'antd';
import {SettingOutlined} from '@ant-design/icons';
import {useNavigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {TaskTemplateValues, TaskTypeValues} from '../../../../features/task';

interface TemplateOption {
    value: string;
    label: string;
    type: string;
}

interface GroupOption {
    label: string;
    value: string;
}

interface BasicSettingsFormProps {
    currentTaskType: string;
    templateOptions: TemplateOption[];
    taskGroupSelectOptions: GroupOption[];
    initialGroup?: string;
    currentTemplate?: string;
}

const BasicSettingsForm: React.FC<BasicSettingsFormProps> = ({
                                                                 currentTaskType,
                                                                 templateOptions,
                                                                 taskGroupSelectOptions,
                                                                 initialGroup,
                                                                 currentTemplate,
                                                             }) => {
    const navigate = useNavigate();
    const {t} = useTranslation();

    // Determine if subscribe-only mode from template
    const isSubOnly = currentTemplate === TaskTemplateValues.PUBSUB_SUB_ONLY;

    return (
        <>
            <Row gutter={16}>
                <Col span={10}>
                    <Form.Item name="taskName" label={t('task.form.taskName')}
                               rules={[{required: true, message: t('task.form.taskNameRequired')}]}>
                        <Input placeholder={t('task.form.taskNamePlaceholder')}/>
                    </Form.Item>
                </Col>
                <Col span={7}>
                    <Form.Item name="taskType" label={t('task.form.taskType')} initialValue={TaskTypeValues.CONN}
                               rules={[{required: true}]}>
                        <Select options={[
                            {label: t('task.type.CONN'), value: TaskTypeValues.CONN},
                            {label: t('task.type.PUBSUB'), value: TaskTypeValues.PUBSUB},
                        ]}/>
                    </Form.Item>
                </Col>
                <Col span={7}>
                    <Form.Item name="template" label={t('task.form.taskTemplate')} initialValue={TaskTemplateValues.CONN_STANDARD}
                               rules={[{required: true}]}>
                        <Select options={templateOptions.filter(t => t.type === currentTaskType).map(t => ({
                            label: t.label,
                            value: t.value
                        }))}/>
                    </Form.Item>
                </Col>
            </Row>
            <Row gutter={16}>
                <Col span={12}>
                    <Form.Item name="group" label={t('task.form.taskGroup')} initialValue={initialGroup}>
                        <Select
                            placeholder={t('task.form.selectTaskGroupPlaceholder')}
                            allowClear
                            options={taskGroupSelectOptions}
                            dropdownRender={(menu) => (
                                <>
                                    {menu}
                                    <Button type="link" icon={<SettingOutlined/>} style={{fontSize: 12}}
                                            onClick={() => navigate('/tasks?tab=groups')}>
                                        {t('task.form.manageTaskGroups')}
                                    </Button>
                                </>
                            )}
                        />
                    </Form.Item>
                </Col>
            </Row>

            {/* Topic config - show for PUBSUB type only */}
            {currentTaskType === TaskTypeValues.PUBSUB && (
                <>
                    <Divider plain>{t('task.form.topicConfig')}</Divider>
                    <Row gutter={16}>
                        <Col span={16}>
                            <Form.Item
                                name="topic"
                                label={
                                    isSubOnly ? t('task.form.subTopic') : t('task.form.pubTopic')
                                }
                                extra={
                                    !isSubOnly && (
                                        <span style={{color: '#999', fontSize: 12}}>
                                            {t('task.form.pubTopicDefaultHint')}
                                        </span>
                                    )
                                }
                            >
                                <Input
                                    placeholder={
                                        isSubOnly
                                            ? t('task.form.subTopicPlaceholder')
                                            : t('task.form.pubTopicExample')
                                    }
                                />
                            </Form.Item>
                        </Col>
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
                </>
            )}
        </>
    );
};

export default BasicSettingsForm;