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

import {setupServer} from 'msw/node'
import {http, HttpResponse} from 'msw'
import * as taskHandlers from './handlers/taskHandlers'
import * as clusterHandlers from './handlers/clusterHandlers'
import * as groupHandlers from './handlers/groupHandlers'
import * as mqttBrokerHandlers from './handlers/mqttBrokerHandlers'

export const server = setupServer(
    ...taskHandlers.default,
    ...clusterHandlers.default,
    ...groupHandlers.default,
    ...mqttBrokerHandlers.default,
    // Fallback handler for unhandled requests
    http.all('*', () => {
        return new HttpResponse(null, {status: 404})
    })
)
