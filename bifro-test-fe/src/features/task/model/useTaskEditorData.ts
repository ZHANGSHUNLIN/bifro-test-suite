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

import {useCallback, useState} from 'react';
import {message} from 'antd';
import {useTranslation} from 'react-i18next';
import {taskApi} from '../api';
import groupApi from '../../../features/group';
import {certificateApi} from '../../certificate';
import type {MqttGroup} from '../../../features/group';
import type {TlsCertificate} from '../../certificate';

interface TemplateOption {
    value: string;
    label: string;
    type: string;
}

interface GroupOption {
    label: string;
    value: string;
}

export const useTaskEditorData = () => {
    const {t} = useTranslation();
    const [templateOptions, setTemplateOptions] = useState<TemplateOption[]>([]);
    const [clientCertOptions, setClientCertOptions] = useState<GroupOption[]>([]);
    const [taskGroupSelectOptions, setTaskGroupSelectOptions] = useState<GroupOption[]>([]);
    const [brokerGroupSelectOptions, setBrokerGroupSelectOptions] = useState<GroupOption[]>([]);

    const loadTemplateOptions = useCallback(async () => {
        try {
            const templates = await taskApi.getTemplates();
            setTemplateOptions(templates.filter(template => template.type !== 'CHAOS'));
        } catch (error) {
            console.error('Failed to load task template options:', error);
        }
    }, []);

    const loadClientCertOptions = useCallback(async () => {
        try {
            const allCerts = await certificateApi.getAllCertificates('CLIENT');
            const options = allCerts.map((cert: TlsCertificate) => ({
                label: cert.name,
                value: cert.id
            }));
            setClientCertOptions(options);
        } catch (error) {
            console.error('Failed to load certificate options:', error);
        }
    }, []);

    const loadTaskGroupSelectOptions = useCallback(async () => {
        try {
            const allGroups = await groupApi.getAllGroupsForSelect('TASK');
            const options = allGroups.map((g: MqttGroup) => ({label: g.name, value: g.id}));
            setTaskGroupSelectOptions(options);
            return options[0]?.value || '';
        } catch (error) {
            console.error('Failed to load task group options:', error);
            message.error(t('task.msg.loadGroupFailed'));
            return '';
        }
    }, [t]);

    const loadBrokerGroupSelectOptions = useCallback(async () => {
        try {
            const allGroups = await groupApi.getAllGroupsForSelect('BROKER');
            const options = allGroups.map((g: MqttGroup) => ({label: g.name, value: g.id}));
            setBrokerGroupSelectOptions(options);
            return options[0]?.value || '';
        } catch (error) {
            console.error('Failed to load broker group options:', error);
            message.error(t('task.msg.loadBrokerGroupFailed'));
            return '';
        }
    }, [t]);

    return {
        templateOptions,
        clientCertOptions,
        taskGroupSelectOptions,
        brokerGroupSelectOptions,
        loadTemplateOptions,
        loadClientCertOptions,
        loadTaskGroupSelectOptions,
        loadBrokerGroupSelectOptions,
    };
};
