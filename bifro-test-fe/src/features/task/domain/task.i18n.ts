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

import i18n from '../../../i18n';
import {TaskStatusValues} from './task.constants';

// Task status text mapping
export const TaskStatusText = {
    [TaskStatusValues.INIT]: i18n.t('task.status.INIT'),
    [TaskStatusValues.ASSIGNED]: i18n.t('task.status.ASSIGNED'),
    [TaskStatusValues.STARTING]: i18n.t('task.status.STARTING'),
    [TaskStatusValues.ONGOING]: i18n.t('task.status.ONGOING'),
    [TaskStatusValues.SHUTTING]: i18n.t('task.status.SHUTTING'),
    [TaskStatusValues.SHUTDOWN]: i18n.t('task.status.SHUTDOWN'),
    [TaskStatusValues.STOPPED]: i18n.t('task.status.STOPPED'),
    [TaskStatusValues.FAILED]: i18n.t('task.status.FAILED'),
    [TaskStatusValues.TIMEOUT]: i18n.t('task.status.TIMEOUT'),
    // Legacy state compatibility
    [TaskStatusValues.START]: i18n.t('task.status.START'),
    [TaskStatusValues.CONNECTING]: i18n.t('task.status.CONNECTING'),
    [TaskStatusValues.INIT_PUB_CLIENT]: i18n.t('task.status.INIT_PUB_CLIENT'),
    [TaskStatusValues.INIT_SUB_CLIENT]: i18n.t('task.status.INIT_SUB_CLIENT'),
    [TaskStatusValues.PUB_SUB_CLIENT_READY]: i18n.t('task.status.PUB_SUB_CLIENT_READY'),
    [TaskStatusValues.PUB_SUB_CLIENT_START]: i18n.t('task.status.PUB_SUB_CLIENT_START'),
    [TaskStatusValues.PUB_CLIENT_CONN]: i18n.t('task.status.PUB_CLIENT_CONN'),
    [TaskStatusValues.SUB_CLIENT_CONN]: i18n.t('task.status.SUB_CLIENT_CONN'),
    [TaskStatusValues.INIT_KAFKA_CLIENT]: i18n.t('task.status.INIT_KAFKA_CLIENT'),
    [TaskStatusValues.PRODUCING]: i18n.t('task.status.PRODUCING'),
    [TaskStatusValues.DATABASE_CONNECTING]: i18n.t('task.status.DATABASE_CONNECTING'),
    [TaskStatusValues.DATABASE_OPERATING]: i18n.t('task.status.DATABASE_OPERATING'),
};
