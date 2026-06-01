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

// TypeScript type definitions for traffic profile library

export type ProfileSource = 'GRAFANA_IMPORT' | 'FORMULA' | 'MANUAL_DRAW';

export type EndBehavior = 'LOOP' | 'HOLD';

export interface WaveformProfile {
    id: string;
    name: string;
    description?: string;
    group?: string;
    
    dataPoints?: number[][];
    totalDurationMs: number;
    
    maxQps: number;
    
    targetTotalCount?: number;
    
    peakQps?: number;
    avgQps?: number;
    
    integral?: number;
    createdAt?: string;
}

export interface CreateProfileRequest {
    name: string;
    description?: string;
    group: string;
    dataPoints: number[][];
    totalDurationMs: number;
    maxQps: number;
    targetTotalCount?: number;
}

export interface ImportProfileRequest {
    file: File;
    name: string;
    description?: string;
}

export interface ProfileRef {
    profileId: string;
    profileName?: string;
    
    dataPoints?: number[][];
    
    integral?: number;
}
