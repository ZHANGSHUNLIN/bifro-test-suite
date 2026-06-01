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

package org.apache.bifromq.testsuite.audit.domain;

public final class AuditAction {

    public static final String AUTH_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
    public static final String AUTH_LOGIN_FAILURE = "AUTH_LOGIN_FAILURE";
    public static final String AUTH_LOGOUT = "AUTH_LOGOUT";
    public static final String TASK_CREATE = "TASK_CREATE";
    public static final String TASK_UPDATE = "TASK_UPDATE";
    public static final String TASK_START = "TASK_START";
    public static final String TASK_STOP = "TASK_STOP";
    public static final String TASK_DELETE = "TASK_DELETE";
    public static final String TASK_ALLOCATE = "TASK_ALLOCATE";
    public static final String BROKER_CREATE = "BROKER_CREATE";
    public static final String BROKER_UPDATE = "BROKER_UPDATE";
    public static final String BROKER_DELETE = "BROKER_DELETE";
    public static final String GROUP_CREATE = "GROUP_CREATE";
    public static final String GROUP_UPDATE = "GROUP_UPDATE";
    public static final String GROUP_DELETE = "GROUP_DELETE";
    public static final String PROFILE_CREATE = "PROFILE_CREATE";
    public static final String PROFILE_UPDATE = "PROFILE_UPDATE";
    public static final String PROFILE_DELETE = "PROFILE_DELETE";
    public static final String CERT_CREATE = "CERT_CREATE";
    public static final String CERT_UPDATE = "CERT_UPDATE";
    public static final String CERT_DELETE = "CERT_DELETE";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String USER_RESET_PASSWORD = "USER_RESET_PASSWORD";
    public static final String USER_CHANGE_PASSWORD = "USER_CHANGE_PASSWORD";

    private AuditAction() {
    }
}
