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

import {createBrowserRouter} from 'react-router-dom';
import AuthGuard from '../components/AuthGuard';
import RoleGuard from '../components/RoleGuard';
import MainLayout from '../layouts/MainLayout';
import {
    AuditLogs,
    ClusterManagement,
    Home,
    Login,
    MqttInstanceManagement,
    SystemUserManagement,
    TaskManagement,
    TlsCertificateManagement,
    TrafficProfileManagement
} from '../pages';

const router = createBrowserRouter([
        {
            path: '/login',
            element: <Login/>
        },
        {
            path: '/',
            element: <AuthGuard/>,
            children: [
                {
                    element: <MainLayout/>,
                    children: [
                        {
                            index: true,
                            element: <Home/>
                        },
                        {
                            path: 'tasks',
                            element: <TaskManagement/>
                        },
                        {
                            path: 'profiles',
                            element: <TrafficProfileManagement/>
                        },
                        {
                            path: 'mqtt-instances',
                            element: <MqttInstanceManagement/>
                        },
                        {
                            path: 'certificates',
                            element: <TlsCertificateManagement/>
                        },
                        {
                            path: 'cluster',
                            element: <ClusterManagement/>
                        },
                        {
                            element: <RoleGuard role="ADMIN"/>,
                            children: [
                                {
                                    path: 'system/audit-logs',
                                    element: <AuditLogs/>
                                },
                                {
                                    path: 'system/users',
                                    element: <SystemUserManagement/>
                                }
                            ]
                        }
                    ]
                }
            ]
        }
    ],
    {
        basename: '/admin'
    });

export default router;
