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
import type {BrokerListItem, MqttBrokerConfig} from '../domain';
import type {PageInfo} from '../../task';

export const brokerApi = {
    getAllBrokers: (enabled?: boolean, group?: string, pageNum?: number, pageSize?: number) => {
        const params: Record<string, string | number | boolean> = {};
        if (enabled !== undefined) params.enabled = enabled;
        if (group !== undefined && group !== '') params.group = group;
        if (pageNum !== undefined) params.pageNum = pageNum;
        if (pageSize !== undefined) params.pageSize = pageSize;
        return api.get<PageInfo<BrokerListItem>>('/broker/list', {params});
    },
    getBrokerDetails: (brokerId: string) => {
        return api.get<MqttBrokerConfig>('/broker/:brokerId', {params: {brokerId}});
    },
    addBroker: (brokerRequest: any) => {
        return api.post<MqttBrokerConfig>('/broker/add', brokerRequest);
    },
    updateBroker: (brokerId: string, brokerRequest: any) => {
        return api.put<MqttBrokerConfig>('/broker/:brokerId', brokerRequest, {params: {brokerId}});
    },
    deleteBroker: (brokerId: string) => {
        return api.delete<void>('/broker/:brokerId', {params: {brokerId}});
    },
    toggleBrokerStatus: (brokerId: string, enabled: boolean) => {
        return api.patch<MqttBrokerConfig>('/broker/:brokerId/status', {enabled}, {params: {brokerId}});
    },
};

export default brokerApi;
