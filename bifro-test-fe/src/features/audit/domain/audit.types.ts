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

export interface AuditLog {
    id: string;
    username?: string;
    action?: string;
    resourceType?: string;
    resourceId?: string;
    clientIp?: string;
    userAgent?: string;
    requestId?: string;
    success: boolean;
    message?: string;
    metadata?: Record<string, unknown>;
    createdAt?: string;
}

export interface AuditLogQuery {
    username?: string;
    action?: string;
    resourceType?: string;
    success?: boolean;
    startTime?: string;
    endTime?: string;
    pageNum?: number;
    pageSize?: number;
}
