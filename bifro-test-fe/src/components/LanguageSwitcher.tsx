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
import {Button, Dropdown, Select, Space} from 'antd';
import type {MenuProps} from 'antd';
import {CheckOutlined, GlobalOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import {
    type LanguagePreference,
    getLanguagePreference,
    getResolvedLanguage,
    LANGUAGE_OPTIONS,
    setLanguagePreference
} from '../i18n';

const {Option} = Select;

interface LanguageSwitcherProps {
    variant?: 'select' | 'button';
}

const LanguageSwitcher: React.FC<LanguageSwitcherProps> = ({variant = 'select'}) => {
    const {t, i18n} = useTranslation();
    const currentPreference = getLanguagePreference();
    const currentLanguage = getResolvedLanguage(i18n.language);

    if (variant === 'button') {
        const selectedLanguage = LANGUAGE_OPTIONS.find(option => option.code === currentLanguage && 'label' in option);
        const menuItems: MenuProps['items'] = LANGUAGE_OPTIONS.map(option => ({
            key: option.code,
            label: (
                <Space size={8} style={{minWidth: 116, justifyContent: 'space-between'}}>
                    <span>{'labelKey' in option ? t(option.labelKey) : option.label}</span>
                    {currentPreference === option.code ? <CheckOutlined style={{fontSize: 12}}/> : null}
                </Space>
            ),
        }));

        return (
            <Dropdown
                trigger={['click']}
                menu={{
                    items: menuItems,
                    selectedKeys: [currentPreference],
                    onClick: ({key}) => setLanguagePreference(key as LanguagePreference),
                }}
            >
                <Button
                    type="text"
                    size="small"
                    icon={<GlobalOutlined/>}
                    aria-label={t('common.language')}
                >
                    {selectedLanguage && 'label' in selectedLanguage ? selectedLanguage.label : currentLanguage.toUpperCase()}
                </Button>
            </Dropdown>
        );
    }

    return (
        <Select
            value={currentPreference}
            onChange={(lang: LanguagePreference) => setLanguagePreference(lang)}
            size="small"
            style={{width: 128}}
            aria-label={t('common.language')}
        >
            {LANGUAGE_OPTIONS.map(option => (
                <Option key={option.code} value={option.code}>
                    {'labelKey' in option ? t(option.labelKey) : option.label}
                </Option>
            ))}
        </Select>
    );
};

export default LanguageSwitcher;
