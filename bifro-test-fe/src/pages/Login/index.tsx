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
import {Button, Card, Form, Input, Typography, message, theme} from 'antd';
import {LockOutlined, UserOutlined} from '@ant-design/icons';
import {Navigate, useLocation, useNavigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import LanguageSwitcher from '../../components/LanguageSwitcher';
import {useAuth} from '../../contexts/AuthContext';

const {Text, Title} = Typography;

interface LoginFormValues {
    username: string;
    password: string;
}

const Login: React.FC = () => {
    const {t} = useTranslation();
    const {token} = theme.useToken();
    const {user, loading, login} = useAuth();
    const [submitting, setSubmitting] = useState(false);
    const navigate = useNavigate();
    const location = useLocation();
    const [messageApi, contextHolder] = message.useMessage();
    const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname || '/';

    useEffect(() => {
        document.title = t('auth.loginTitle');
    }, [t]);

    if (!loading && user && (!user.enabled || user.authenticated)) {
        return <Navigate to={from} replace/>;
    }

    const handleSubmit = async (values: LoginFormValues) => {
        setSubmitting(true);
        try {
            const nextUser = await login(values);
            if (!nextUser.enabled || nextUser.authenticated) {
                navigate(from, {replace: true});
                return;
            }
            messageApi.error(t('auth.loginFailed'));
        } catch (error) {
            messageApi.error(error instanceof Error ? error.message : t('auth.loginFailed'));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div
            style={{
                minHeight: '100vh',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: 24,
                background: token.colorBgLayout,
            }}
        >
            {contextHolder}
            <Card style={{width: 380, borderRadius: 8}}>
                <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, marginBottom: 24}}>
                    <div>
                        <Title level={3} style={{margin: 0}}>{t('auth.loginTitle')}</Title>
                        <Text type="secondary">{t('auth.loginSubtitle')}</Text>
                    </div>
                    <LanguageSwitcher variant="button"/>
                </div>
                <Form layout="vertical" onFinish={handleSubmit} requiredMark={false}>
                    <Form.Item
                        name="username"
                        label={t('auth.username')}
                        rules={[{required: true, message: t('auth.usernameRequired')}]}
                    >
                        <Input prefix={<UserOutlined/>} autoComplete="username"/>
                    </Form.Item>
                    <Form.Item
                        name="password"
                        label={t('auth.password')}
                        rules={[{required: true, message: t('auth.passwordRequired')}]}
                    >
                        <Input.Password prefix={<LockOutlined/>} autoComplete="current-password"/>
                    </Form.Item>
                    <Button type="primary" htmlType="submit" block loading={submitting}>
                        {t('auth.login')}
                    </Button>
                </Form>
            </Card>
        </div>
    );
};

export default Login;
