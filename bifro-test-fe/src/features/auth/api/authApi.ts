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

import {api} from '../../../utils/request';
import type {AuthUser, ChangePasswordRequest, LoginRequest} from '../domain';

export const authApi = {
    login: (request: LoginRequest) => api.post<AuthUser>('/auth/login', request),
    logout: () => api.post<void>('/auth/logout'),
    me: () => api.get<AuthUser>('/auth/me'),
    changePassword: (request: ChangePasswordRequest) => api.post<void>('/auth/change-password', request),
};

export default authApi;
