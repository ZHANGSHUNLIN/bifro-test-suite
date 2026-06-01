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

// Task type enum
export type TaskType = 'CONN' | 'PUBSUB' | 'CHAOS';
export const TaskTypeValues = {CONN: 'CONN', PUBSUB: 'PUBSUB', CHAOS: 'CHAOS'} as const;

// Task stage enum
export type TaskStage =
    | 'INIT'
    | 'ASSIGNED'
    | 'START'
    | 'INIT_CLIENT'
    | 'INIT_PUB_CLIENT'
    | 'INIT_SUB_CLIENT'
    | 'PUB_SUB_CLIENT_READY'
    | 'PUB_SUB_CLIENT_START'
    | 'PUB_CLIENT_CONN'
    | 'SUB_CLIENT_CONN'
    | 'SUBSCRIBE_CLIENT'
    | 'INIT_KAFKA_CLIENT'
    | 'PRODUCING'
    | 'DATABASE_CONNECTING'
    | 'DATABASE_OPERATING'
    | 'ONGOING'
    | 'SHUTTING'
    | 'SHUTDOWN'
    | 'STOPPED'
    | 'FAILED'
    | 'TIMEOUT';

// Task template type (extensible, not hardcoded)
export type TaskTemplate = string;
export const TaskTemplateValues = {
    CONN_STANDARD: 'CONN_STANDARD',
    CONN_PUBLISH_ON_CONNECT: 'CONN_PUBLISH_ON_CONNECT',
    // CONN_IMMEDIATE_DISCONNECT intentionally omitted — not yet implemented
    PUBSUB_STANDARD: 'PUBSUB_STANDARD',
    PUBSUB_PUB_ONLY: 'PUBSUB_PUB_ONLY',
    PUBSUB_SUB_ONLY: 'PUBSUB_SUB_ONLY',
    CHAOS_STANDARD: 'CHAOS_STANDARD',
} as const;

// Protocol type
export type Protocol = 'mqtt' | 'mqtts' | 'ws' | 'wss';

// MQTT QoS level
export type MqttQoS = 0 | 1 | 2;

export const MqttQoSValues = {
    AT_MOST_ONCE: 0,
    AT_LEAST_ONCE: 1,
    EXACTLY_ONCE: 2,
} as const;

// Task status enum
export type TaskStatus =
    'INIT'
    | 'ASSIGNED'
    | 'STARTING'
    | 'ONGOING'
    | 'SHUTTING'
    | 'SHUTDOWN'
    | 'STOPPED'
    | 'FAILED'
    | 'TIMEOUT'
    // Legacy states, kept for compatibility (server may run old version)
    | 'START'
    | 'CONNECTING'
    | 'INIT_PUB_CLIENT'
    | 'INIT_SUB_CLIENT'
    | 'PUB_SUB_CLIENT_READY'
    | 'PUB_SUB_CLIENT_START'
    | 'PUB_CLIENT_CONN'
    | 'SUB_CLIENT_CONN'
    | 'SUBSCRIBE_CLIENT'
    | 'INIT_KAFKA_CLIENT'
    | 'PRODUCING'
    | 'DATABASE_CONNECTING'
    | 'DATABASE_OPERATING';

export const TaskStatusValues = {
    INIT: 'INIT',
    ASSIGNED: 'ASSIGNED',
    STARTING: 'STARTING',
    ONGOING: 'ONGOING',
    SHUTTING: 'SHUTTING',
    SHUTDOWN: 'SHUTDOWN',
    STOPPED: 'STOPPED',
    FAILED: 'FAILED',
    TIMEOUT: 'TIMEOUT',
    // Legacy state, kept for compatibility
    START: 'START',
    CONNECTING: 'CONNECTING',
    INIT_PUB_CLIENT: 'INIT_PUB_CLIENT',
    INIT_SUB_CLIENT: 'INIT_SUB_CLIENT',
    PUB_SUB_CLIENT_READY: 'PUB_SUB_CLIENT_READY',
    PUB_SUB_CLIENT_START: 'PUB_SUB_CLIENT_START',
    PUB_CLIENT_CONN: 'PUB_CLIENT_CONN',
    SUB_CLIENT_CONN: 'SUB_CLIENT_CONN',
    SUBSCRIBE_CLIENT: 'SUBSCRIBE_CLIENT',
    INIT_KAFKA_CLIENT: 'INIT_KAFKA_CLIENT',
    PRODUCING: 'PRODUCING',
    DATABASE_CONNECTING: 'DATABASE_CONNECTING',
    DATABASE_OPERATING: 'DATABASE_OPERATING',
} as const;
