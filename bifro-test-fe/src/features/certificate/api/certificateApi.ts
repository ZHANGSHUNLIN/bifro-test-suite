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
import type {CertType, TlsCertificate, TlsCertificateCreateReq, TlsCertificateUpdateReq} from '../domain';
import type {PageInfo} from '../../task';

export const certificateApi = {
    getCertificates: (type?: CertType, pageNum?: number, pageSize?: number, keyword?: string) => {
        const params: Record<string, number | string> = {};
        if (type !== undefined) params.type = type;
        if (pageNum !== undefined) params.pageNum = pageNum;
        if (pageSize !== undefined) params.pageSize = pageSize;
        if (keyword !== undefined && keyword.trim() !== '') params.keyword = keyword.trim();
        return api.get<PageInfo<TlsCertificate>>('/certificates', {params});
    },
    getAllCertificates: (type: CertType) => {
        return api.get<TlsCertificate[]>('/certificates/all', {params: {type}});
    },
    getCertificate: (id: string) => {
        return api.get<TlsCertificate>('/certificates/:id', {params: {id}});
    },
    createCertificate: (request: TlsCertificateCreateReq) => {
        return api.post<TlsCertificate>('/certificates', request);
    },
    updateCertificate: (id: string, request: TlsCertificateUpdateReq) => {
        return api.put<TlsCertificate>('/certificates/:id', request, {params: {id}});
    },
    deleteCertificate: (id: string) => {
        return api.delete<void>('/certificates/:id', {params: {id}});
    }
};

export default certificateApi;
