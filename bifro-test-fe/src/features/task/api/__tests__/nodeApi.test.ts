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

import {describe, expect, it} from 'vitest'
import {nodeApi} from '../../../node/api/nodeApi'

// Contract tests depend on real backend service and specific test data, only run when VITEST_CONTRACT=true
const runContract = process.env.VITEST_CONTRACT === 'true'

describe.skipIf(!runContract)('nodeApi | contract tests', () => {
    describe('getNodeMetrics', () => {
        it('getNodeMetrics_withNodeId_returnsMetricsResponse', async () => {
            // when
            const response = await nodeApi.getNodeMetrics('node1', 'task1234')

            // then - verify response structure matches NodeMetricsResponse
            expect(response).toHaveProperty('nodeId')
            expect(response).toHaveProperty('success')
            expect(response).toHaveProperty('counterMetrics')
            expect(response).toHaveProperty('timerMetrics')
            expect(response.success).toBe(true)
            expect(response.counterMetrics.length).toBeGreaterThan(0)
            expect(response.timerMetrics.length).toBeGreaterThan(0)
        })

        it('getNodeMetrics_withOfflineNode_returnsOfflineResponse', async () => {
            // when
            const response = await nodeApi.getNodeMetrics('offline-node', 'task1234')

            // then
            expect(response.success).toBe(false)
            expect(response.errorCode).toBe('NODE_OFFLINE')
        })
    })

    describe('getClientInstances', () => {
        it('getClientInstances_withValidParams_returnsClientInstances', async () => {
            // when
            const response = await nodeApi.getClientInstances('node1', 'task1234', 'conn', 0, 20)

            // then - verify response structure matches ClientInstanceResponse
            expect(response).toHaveProperty('success')
            expect(response).toHaveProperty('clients')
            expect(response).toHaveProperty('total')
        })
    })
})
