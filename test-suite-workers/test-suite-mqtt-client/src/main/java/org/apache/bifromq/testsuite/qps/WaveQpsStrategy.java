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

public class WaveQpsStrategy implements QpsStrategy {

    private final WaveQpsSpec spec;

    public WaveQpsStrategy(WaveQpsSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("WaveQpsSpec must not be null");
        }
        if (spec.getBaseQps() <= 0) {
            throw new IllegalArgumentException("baseQps must be positive: " + spec.getBaseQps());
        }
        if (spec.getTotalDurationMs() <= 0) {
            throw new IllegalArgumentException("totalDurationMs must be positive: " + spec.getTotalDurationMs());
        }
        this.spec = spec;
    }

    @Override
    public int currentQps(long elapsedMs) {
        return (int) Math.round(currentQpsValue(elapsedMs));
    }

    @Override
    public double currentQpsValue(long elapsedMs) {
        double qps = spec.getBaseQps();
        for (WaveQpsSpec.Component c : spec.getComponents()) {
            double period = spec.getTotalDurationMs() * c.getPeriodFraction();
            qps += c.getAmplitude() * Math.sin(2 * Math.PI * elapsedMs / period + c.getPhase());
        }
        return Math.max(1.0d, qps);
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
