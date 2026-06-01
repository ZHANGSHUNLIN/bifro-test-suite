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

package org.apache.bifromq.testsuite.qps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class DataDrivenQpsStrategyTest {

    
    private ProfileQpsSpec buildSpec(ProfileQpsSpec.EndBehavior endBehavior) {
        return ProfileQpsSpec.builder()
            .dataPoints(List.of(
                new long[] {0L, 100L},
                new long[] {5000L, 500L},
                new long[] {10000L, 200L}
            ))
            .totalDurationMs(10000L)
            .endBehavior(endBehavior)
            .build();
    }

    

    @Test
    void currentQps_exactPointLookup_returnsCorrectValue() {
        
        DataDrivenQpsStrategy strategy = new DataDrivenQpsStrategy(buildSpec(ProfileQpsSpec.EndBehavior.HOLD));

        assertThat(strategy.currentQps(0)).isEqualTo(100);
        assertThat(strategy.currentQps(5000)).isEqualTo(500);
        
        assertThat(strategy.currentQps(10000)).isEqualTo(200);
    }

    

    @Test
    void currentQps_interpolation_midpointIsCorrect() {
        DataDrivenQpsStrategy strategy = new DataDrivenQpsStrategy(buildSpec(ProfileQpsSpec.EndBehavior.LOOP));

        
        assertThat(strategy.currentQps(2500)).isEqualTo(300);

        
        assertThat(strategy.currentQps(7500)).isEqualTo(350);
    }

    

    @Test
    void currentQps_loopEndBehavior_wrapsElapsedTime() {
        DataDrivenQpsStrategy strategy = new DataDrivenQpsStrategy(buildSpec(ProfileQpsSpec.EndBehavior.LOOP));

        
        assertThat(strategy.currentQps(0)).isEqualTo(100);
        assertThat(strategy.currentQps(10000)).isEqualTo(100); 

        
        assertThat(strategy.currentQps(12500)).isEqualTo(strategy.currentQps(2500));

        
        assertThat(strategy.currentQps(15000)).isEqualTo(strategy.currentQps(5000));
    }

    

    @Test
    void currentQps_holdEndBehavior_clampsToLastPoint() {
        DataDrivenQpsStrategy strategy = new DataDrivenQpsStrategy(buildSpec(ProfileQpsSpec.EndBehavior.HOLD));

        
        assertThat(strategy.currentQps(10001)).isEqualTo(200);
        assertThat(strategy.currentQps(999999)).isEqualTo(200);
    }

    

    @Test
    void currentQps_negativeQpsInData_isClampedToOne() {
        ProfileQpsSpec spec = ProfileQpsSpec.builder()
            .dataPoints(List.of(
                new long[] {0L, 0L},
                new long[] {1000L, 0L}
            ))
            .totalDurationMs(1000L)
            .endBehavior(ProfileQpsSpec.EndBehavior.HOLD)
            .build();

        DataDrivenQpsStrategy strategy = new DataDrivenQpsStrategy(spec);
        
        assertThat(strategy.currentQps(0)).isGreaterThanOrEqualTo(1);
        assertThat(strategy.currentQps(500)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void fromProfileDataPoints_defaultEndBehaviorHoldsLastPoint() {
        QpsStrategy strategy = QpsStrategy.fromProfileDataPoints(
            List.of(new long[] {0, 0}, new long[] {1_000, 100}, new long[] {2_000, 20}), 1);

        assertThat(strategy.currentQps(2_500)).isEqualTo(20);
    }

    @Test
    void fromProfileDataPoints_loopEndBehaviorWrapsElapsedTime() {
        QpsStrategy strategy = QpsStrategy.fromProfileDataPoints(
            List.of(new long[] {0, 0}, new long[] {1_000, 100}, new long[] {2_000, 0}),
            1,
            ProfileQpsSpec.EndBehavior.LOOP);

        assertThat(strategy.currentQps(2_500)).isEqualTo(50);
    }

    @Test
    void fromProfileDataPoints_preservesZeroQpsForDynamicPause() {
        QpsStrategy strategy = QpsStrategy.fromProfileDataPoints(
            List.of(new long[] {0, 0}, new long[] {1_000, 0}), 1);

        assertThat(strategy.currentQps(500)).isZero();
    }

    

    @Test
    void constructor_nullSpec_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new DataDrivenQpsStrategy(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_emptyDataPoints_throwsIllegalArgumentException() {
        ProfileQpsSpec spec = ProfileQpsSpec.builder()
            .dataPoints(List.of())
            .totalDurationMs(1000L)
            .endBehavior(ProfileQpsSpec.EndBehavior.LOOP)
            .build();

        assertThatThrownBy(() -> new DataDrivenQpsStrategy(spec))
            .isInstanceOf(IllegalArgumentException.class);
    }

    

    @Test
    void isDynamic_returnsTrue() {
        DataDrivenQpsStrategy strategy = new DataDrivenQpsStrategy(buildSpec(ProfileQpsSpec.EndBehavior.LOOP));
        assertThat(strategy.isDynamic()).isTrue();
    }
}
