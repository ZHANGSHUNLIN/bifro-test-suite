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
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class WaveQpsStrategyTest {

    

    @Test
    void waveQps_singleComponent_correctAtQuarterPeriodPoints() {
        
        WaveQpsSpec spec = WaveQpsSpec.builder()
            .baseQps(1000)
            .totalDurationMs(10000)
            .components(List.of(
                WaveQpsSpec.Component.builder()
                    .amplitude(500)
                    .periodFraction(1.0)
                    .phase(0)
                    .build()
            ))
            .build();
        WaveQpsStrategy strategy = new WaveQpsStrategy(spec);

        
        assertThat(strategy.currentQps(0)).isEqualTo(1000);

        
        assertThat(strategy.currentQps(2500)).isCloseTo(1500, within(1));

        
        assertThat(strategy.currentQps(5000)).isCloseTo(1000, within(2));

        
        assertThat(strategy.currentQps(7500)).isCloseTo(500, within(1));
    }

    

    @Test
    void waveQps_negativeResult_clampedToMinimumOne() {
        
        WaveQpsSpec spec = WaveQpsSpec.builder()
            .baseQps(100)
            .totalDurationMs(10000)
            .components(List.of(
                WaveQpsSpec.Component.builder()
                    .amplitude(200)
                    .periodFraction(1.0)
                    .phase(3 * Math.PI / 2)
                    .build()
            ))
            .build();
        WaveQpsStrategy strategy = new WaveQpsStrategy(spec);

        
        int qps = strategy.currentQps(0);
        assertThat(qps).isEqualTo(1);
    }

    

    @Test
    void waveQps_multiComponent_noOverflowAndMinProtected() {
        
        WaveQpsSpec spec = WaveQpsSpec.builder()
            .baseQps(500)
            .totalDurationMs(10000)
            .components(List.of(
                WaveQpsSpec.Component.builder()
                    .amplitude(300)
                    .periodFraction(0.5)
                    .phase(0)
                    .build(),
                WaveQpsSpec.Component.builder()
                    .amplitude(200)
                    .periodFraction(0.3)
                    .phase(Math.PI / 4)
                    .build()
            ))
            .build();
        WaveQpsStrategy strategy = new WaveQpsStrategy(spec);

        
        int theoreticalMax = 500 + 300 + 200 + 1;
        for (long t = 0; t <= 10000; t += 100) {
            int qps = strategy.currentQps(t);
            assertThat(qps)
                .as("QPS at t=%d must be >= 1", t)
                .isGreaterThanOrEqualTo(1);
            assertThat(qps)
                .as("QPS at t=%d must not exceed theoretical max %d", t, theoreticalMax)
                .isLessThanOrEqualTo(theoreticalMax);
        }
    }

    

    @Test
    void isDynamic_returnsTrue() {
        WaveQpsSpec spec = WaveQpsSpec.builder()
            .baseQps(100)
            .totalDurationMs(5000)
            .build();
        assertThat(new WaveQpsStrategy(spec).isDynamic()).isTrue();
    }

    

    @Test
    void constructor_nullSpec_throwsIllegalArgument() {
        assertThatThrownBy(() -> new WaveQpsStrategy(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nonPositiveBaseQps_throwsIllegalArgument() {
        WaveQpsSpec spec = WaveQpsSpec.builder()
            .baseQps(0)
            .totalDurationMs(5000)
            .build();
        assertThatThrownBy(() -> new WaveQpsStrategy(spec))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nonPositiveTotalDuration_throwsIllegalArgument() {
        WaveQpsSpec spec = WaveQpsSpec.builder()
            .baseQps(100)
            .totalDurationMs(0)
            .build();
        assertThatThrownBy(() -> new WaveQpsStrategy(spec))
            .isInstanceOf(IllegalArgumentException.class);
    }

    

    @Test
    void waveQps_noComponents_returnsBaseQps() {
        WaveQpsSpec spec = WaveQpsSpec.builder()
            .baseQps(200)
            .totalDurationMs(5000)
            .build();
        WaveQpsStrategy strategy = new WaveQpsStrategy(spec);

        assertThat(strategy.currentQps(0)).isEqualTo(200);
        assertThat(strategy.currentQps(2500)).isEqualTo(200);
    }
}
