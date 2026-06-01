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

// Group management API service (handles both BROKER and TASK group types)
import {api} from '../../../utils/request';
import type {GroupListItem, GroupRequest, MqttGroup} from '../domain';
import type {PageInfo} from '../../task';

export type GroupType = 'BROKER' | 'TASK' | 'PROFILE';

export const groupApi = {
    // Get group list (paginated)
    getAllGroups: <T = GroupListItem>(type: GroupType, pageNum?: number, pageSize?: number, name?: string) => {
        const params: Record<string, number | string> = {type};
        if (pageNum !== undefined) {
            params.pageNum = pageNum;
        }
        if (pageSize !== undefined) {
            params.pageSize = pageSize;
        }
        if (name !== undefined && name.trim() !== '') {
            params.name = name.trim();
        }
        return api.get<PageInfo<T>>('/groups/list', {params});
    },

    // Get all groups (no pagination, for dropdown)
    getAllGroupsForSelect: (type: GroupType) => {
        return api.get<MqttGroup[]>('/groups/all', {params: {type}});
    },

    // Get group details
    getGroupDetail: (id: string) => {
        return api.get<MqttGroup>('/groups/:id', {params: {id}});
    },

    // Add Group
    addGroup: (type: GroupType, request: GroupRequest) => {
        return api.post<MqttGroup>('/groups', request, {params: {type}});
    },

    // Update group
    updateGroup: (id: string, request: GroupRequest) => {
        return api.put<MqttGroup>('/groups/:id', request, {params: {id}});
    },

    // deletegroup
    deleteGroup: (type: GroupType, id: string) => {
        return api.delete<void>('/groups/:id', {params: {id, type}});
    }
};

export default groupApi;
