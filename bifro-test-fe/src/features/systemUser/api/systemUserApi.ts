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
import type {PageInfo} from '../../task';
import type {
    CreateSystemUserRequest,
    ResetPasswordRequest,
    SystemUser,
    UpdateSystemUserRequest
} from '../domain';

export const systemUserApi = {
    list: (pageNum: number = 1, pageSize: number = 20) =>
        api.get<PageInfo<SystemUser>>('/system/users', {params: {pageNum, pageSize}}),
    create: (request: CreateSystemUserRequest) => api.post<SystemUser>('/system/users', request),
    update: (id: string, request: UpdateSystemUserRequest) => api.put<SystemUser>(`/system/users/${id}`, request),
    delete: (id: string) => api.delete<void>(`/system/users/${id}`),
    resetPassword: (id: string, request: ResetPasswordRequest) =>
        api.post<void>(`/system/users/${id}/reset-password`, request),
};

export default systemUserApi;
