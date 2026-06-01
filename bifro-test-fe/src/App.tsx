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
import {RouterProvider} from 'react-router-dom';
import {App as AntApp, ConfigProvider, theme as antTheme} from 'antd';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import {useTranslation} from 'react-i18next';
import router from './router';
import {getResolvedLanguage} from './i18n';
import {AuthProvider} from './contexts/AuthContext';
import {ThemeProvider, useTheme} from './contexts/ThemeContext';
import 'antd/dist/reset.css';
import './index.css';

// Bifro color system
const BIFRO_PRIMARY = '#00b173';
const BIFRO_PRIMARY_HOVER = '#00d68a';

const ThemedApp: React.FC = () => {
    const {isDark} = useTheme();
    const {i18n} = useTranslation();
    const antdLocale = getResolvedLanguage(i18n.language) === 'zh' ? zhCN : enUS;

    const lightTokens = {
        colorPrimary: BIFRO_PRIMARY,
        colorPrimaryHover: BIFRO_PRIMARY_HOVER,
        colorBgContainer: '#ffffff',
        colorBgLayout: '#f0f2f5',
        colorBgElevated: '#ffffff',
        colorBorder: '#e8e8e8',
        colorBorderSecondary: '#f0f0f0',
        colorText: '#1d2129',
        colorTextSecondary: '#4e5969',
        colorTextTertiary: '#86909c',
        colorFillQuaternary: '#f7f8fa',
        colorFillTertiary: '#f2f3f5',
        borderRadius: 6,
        borderRadiusLG: 8,
        borderRadiusSM: 4,
        fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', Arial, sans-serif",
        fontSize: 13,
        boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
        boxShadowSecondary: '0 4px 16px rgba(0,0,0,0.10)',
    };

    const darkTokens = {
        colorPrimary: BIFRO_PRIMARY,
        colorPrimaryHover: BIFRO_PRIMARY_HOVER,
        colorBgContainer: '#1d2535',
        colorBgLayout: '#141b2a',
        colorBgElevated: '#242d3f',
        colorBorder: '#2d3a50',
        colorBorderSecondary: '#253045',
        colorText: '#e8eaf0',
        colorTextSecondary: '#a6b0c3',
        colorTextTertiary: '#6b7a99',
        colorFillQuaternary: '#1a2336',
        colorFillTertiary: '#1f2d42',
        borderRadius: 6,
        borderRadiusLG: 8,
        borderRadiusSM: 4,
        fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', Arial, sans-serif",
        fontSize: 13,
        boxShadow: '0 1px 4px rgba(0,0,0,0.3)',
        boxShadowSecondary: '0 4px 16px rgba(0,0,0,0.4)',
    };

    return (
        <ConfigProvider
            locale={antdLocale}
            theme={{
                algorithm: isDark ? antTheme.darkAlgorithm : antTheme.defaultAlgorithm,
                token: isDark ? darkTokens : lightTokens,
                components: {
                    Layout: {
                        siderBg: '#1a1f2e',
                        triggerBg: '#252c3f',
                        triggerColor: 'rgba(255,255,255,0.65)',
                        headerBg: isDark ? '#1d2535' : '#ffffff',
                        headerHeight: 56,
                    },
                    Menu: {
                        darkItemBg: '#1a1f2e',
                        darkSubMenuItemBg: '#141b26',
                        darkItemSelectedBg: 'rgba(0,177,115,0.15)',
                        darkItemSelectedColor: BIFRO_PRIMARY,
                        darkItemHoverBg: 'rgba(255,255,255,0.06)',
                        darkItemHoverColor: '#ffffff',
                        darkItemColor: 'rgba(255,255,255,0.65)',
                        itemSelectedBg: 'rgba(0,177,115,0.10)',
                        itemSelectedColor: BIFRO_PRIMARY,
                        itemHoverBg: 'rgba(0,177,115,0.06)',
                    },
                    Table: {
                        headerBg: isDark ? '#1a2236' : '#f7f8fa',
                        rowHoverBg: isDark ? 'rgba(0,177,115,0.06)' : 'rgba(0,177,115,0.04)',
                        borderColor: isDark ? '#2d3a50' : '#f0f0f0',
                    },
                    Card: {
                        headerBg: isDark ? '#1a2236' : '#fafafa',
                    },
                    Button: {
                        defaultBorderColor: isDark ? '#2d3a50' : '#d9d9d9',
                    },
                    Tabs: {
                        inkBarColor: BIFRO_PRIMARY,
                        itemSelectedColor: BIFRO_PRIMARY,
                        itemHoverColor: BIFRO_PRIMARY,
                    },
                    Tag: {
                        defaultBg: isDark ? '#1a2236' : '#f0f0f0',
                    },
                },
            }}
        >
            <AntApp>
                <RouterProvider router={router}/>
            </AntApp>
        </ConfigProvider>
    );
};

const App: React.FC = () => (
    <ThemeProvider>
        <AuthProvider>
            <ThemedApp/>
        </AuthProvider>
    </ThemeProvider>
);

export default App;
