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

// TypeScript type definitions for cluster management

export interface MemoryInfo {
    max: number;
    total: number;
    used: number;
    free: number;
}

export interface CpuInfo {
    processors: number;
    loadAverage: number;
}

export interface NetworkInterfaceInfo {
    name: string;
    displayName?: string;
    up: boolean;
    loopback: boolean;
    virtual: boolean;
    multicastSupported: boolean;
    mtu: number;
    addresses: string[];
}

// Node status enum
export const NodeStatus = {
    ONLINE: 'ONLINE',
    OFFLINE: 'OFFLINE',
    UNSTABLE: 'UNSTABLE'
} as const;

export type NodeStatus = typeof NodeStatus[keyof typeof NodeStatus];

export type NodeRole = 'CONTROL' | 'WORKER' | 'ALL' | 'UNKNOWN';
export type StorageMode = 'DATABASE' | 'EMBEDDED';

// Backend node list item (corresponds to NodeListVO)
export interface NodeListVO {
    nodeId: string;
    nodeName: string;
    role?: NodeRole;
    storageMode?: StorageMode;
    embeddedDataDir?: string;
    schedulable?: boolean;
    host: string;
    alive: boolean;
    lastHeartbeatAt: number;
    memory: MemoryInfo;
    cpu: CpuInfo;
    networkInterfaces: NetworkInterfaceInfo[];
}

// Cluster statistics (computed on frontend)
export interface ClusterStatistics {
    totalNodes: number;
    onlineNodes: number;
    offlineNodes: number;
    totalMemory: number;
    usedMemory: number;
    averageCpuLoad: number;
}

// Node task info (corresponds to NodeTaskVO)
export interface NodeTaskInfo {
    taskId: string;
    taskName: string | null;
    currentStage: string;
    totalClientCount: number;
    nodeId: string;
    nodeName: string | null;
}

export interface LocalPortModeConfig {
    enabled: boolean;
    startPort: number;
    endPort: number;
}
