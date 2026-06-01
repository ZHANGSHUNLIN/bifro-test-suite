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

package org.apache.bifromq.testsuite.worker.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FiniteDispatchPlanTest {

    @Test
    void expectedCountAt_shouldIntegrateLinearProfileAndCapAtTotalCount() {
        FiniteDispatchPlan plan = FiniteDispatchPlan.fromProfileDataPoints(
            List.of(new long[] {0, 0}, new long[] {1_000, 10}, new long[] {2_000, 10}),
            12);

        assertThat(plan).isNotNull();
        assertThat(plan.expectedCountAt(0)).isZero();
        assertThat(plan.expectedCountAt(1_000)).isEqualTo(5);
        assertThat(plan.expectedCountAt(1_500)).isEqualTo(10);
        assertThat(plan.expectedCountAt(2_000)).isEqualTo(12);
        assertThat(plan.plannedTotalCount()).isEqualTo(12);
    }

    @Test
    void fromProfileDataPoints_withInvalidInput_shouldReturnNull() {
        assertThat(FiniteDispatchPlan.fromProfileDataPoints(null, 10)).isNull();
        assertThat(FiniteDispatchPlan.fromProfileDataPoints(List.of(new long[] {0, 1}), 10)).isNull();
        assertThat(FiniteDispatchPlan.fromProfileDataPoints(List.of(new long[] {0, 1}, new long[] {1, 1}), 0))
            .isNull();
    }
}
