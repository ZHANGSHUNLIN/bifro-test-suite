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
import {Collapse, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {authPlaceholderGuide} from '../../../../features/task/domain/templatePlaceholders';

const {Text} = Typography;

const AuthPlaceholderGuide: React.FC = () => {
    const {t} = useTranslation();
    const guide = authPlaceholderGuide().map(({key, descKey}) => ({key, desc: t(descKey)}));

    return (
        <Collapse
            ghost
            size="small"
            style={{marginTop: -12, marginBottom: 16}}
            items={[{
                key: 'auth-guide',
                label: <Text type="secondary" style={{fontSize: 12}}>{t('task.form.authPlaceholderGuide')}</Text>,
                children: (
                    <table style={{fontSize: 12, width: '100%', borderCollapse: 'collapse'}}>
                        <thead>
                        <tr style={{background: '#fafafa'}}>
                            <th style={{padding: '4px 8px', textAlign: 'left', border: '1px solid #f0f0f0'}}>
                                {t('task.form.placeholderCol')}
                            </th>
                            <th style={{padding: '4px 8px', textAlign: 'left', border: '1px solid #f0f0f0'}}>
                                {t('task.form.placeholderDescCol')}
                            </th>
                        </tr>
                        </thead>
                        <tbody>
                        {guide.map(({key, desc}) => (
                            <tr key={key}>
                                <td style={{
                                    padding: '4px 8px',
                                    fontFamily: 'monospace',
                                    border: '1px solid #f0f0f0',
                                }}>{key}</td>
                                <td style={{padding: '4px 8px', border: '1px solid #f0f0f0'}}>{desc}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                ),
            }]}
        />
    );
};

export default AuthPlaceholderGuide;
