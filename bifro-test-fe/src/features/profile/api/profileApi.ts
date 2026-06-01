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
import i18n from '../../../i18n';
import type {CreateProfileRequest, WaveformProfile} from '../domain';
import type {PageInfo} from '../../task';

function calcIntegralFromDataPoints(dataPoints?: number[][]): number | undefined {
    if (!dataPoints || dataPoints.length < 2) {
        return undefined;
    }
    let sum = 0;
    for (let i = 0; i < dataPoints.length - 1; i++) {
        sum += (dataPoints[i][1] + dataPoints[i + 1][1]) / 2
            * (dataPoints[i + 1][0] - dataPoints[i][0]) / 1000;
    }
    return Math.round(sum);
}

function normalizeProfileIntegral(profile: WaveformProfile): WaveformProfile {
    const recomputed = calcIntegralFromDataPoints(profile.dataPoints);
    if (recomputed == null) {
        return profile;
    }
    return {
        ...profile,
        integral: recomputed,
    };
}

export function listProfilesPage(
    keyword?: string,
    pageNum: number = 1,
    pageSize: number = 20,
    group?: string
): Promise<PageInfo<WaveformProfile>> {
    const params: Record<string, string | number> = {pageNum, pageSize};
    if (keyword && keyword.trim()) {
        params.keyword = keyword.trim();
    }
    if (group && group.trim()) {
        params.group = group.trim();
    }
    return api.get<PageInfo<WaveformProfile>>('/profile', {params})
        .then(page => ({
            ...page,
            content: (page.content || []).map(normalizeProfileIntegral),
        }));
}

export function listProfiles(keyword?: string): Promise<WaveformProfile[]> {
    return listProfilesPage(keyword, 1, 1000)
        .then(page => page.content || []);
}

export function getProfile(id: string): Promise<WaveformProfile> {
    return api.get<WaveformProfile>(`/profile/${id}`)
        .then(normalizeProfileIntegral);
}

export function getProfilePreview(id: string): Promise<number[][]> {
    return api.get<number[][]>(`/profile/${id}/preview`);
}

export function createProfile(req: CreateProfileRequest): Promise<WaveformProfile> {
    return api.post<WaveformProfile>('/profile', req)
        .then(normalizeProfileIntegral);
}

export async function importProfile(file: File, name: string, description?: string): Promise<WaveformProfile> {
    const form = new FormData();
    form.append('file', file);
    form.append('name', name);
    if (description) form.append('description', description);

    const res = await fetch('/api/profile/import', {
        method: 'POST',
        body: form,
    });
    const json = await res.json();
    if (!res.ok || json.code !== 200) {
        throw new Error(json.message || i18n.t('profile.msg.importFailed'));
    }
    return normalizeProfileIntegral(json.data as WaveformProfile);
}

export function deleteProfile(id: string): Promise<void> {
    return api.delete<void>(`/profile/${id}`);
}

export function updateProfile(id: string, req: CreateProfileRequest): Promise<WaveformProfile> {
    return api.put<WaveformProfile>(`/profile/${id}`, req)
        .then(normalizeProfileIntegral);
}
