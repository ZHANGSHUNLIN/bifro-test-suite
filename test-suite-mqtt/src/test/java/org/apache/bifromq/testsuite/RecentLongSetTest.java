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

package org.apache.bifromq.testsuite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecentLongSetTest {
    @Test
    void add_returnsFalseForDuplicateInsideWindow() {
        RecentLongSet set = new RecentLongSet(3);

        assertThat(set.add(1L)).isTrue();
        assertThat(set.add(1L)).isFalse();
        assertThat(set.size()).isEqualTo(1);
    }

    @Test
    void add_evictsOldestValueWhenWindowIsFull() {
        RecentLongSet set = new RecentLongSet(2);

        assertThat(set.add(1L)).isTrue();
        assertThat(set.add(2L)).isTrue();
        assertThat(set.add(3L)).isTrue();

        assertThat(set.add(1L)).isTrue();
        assertThat(set.add(2L)).isTrue();
        assertThat(set.size()).isEqualTo(2);
    }

    @Test
    void add_keepsRecentValuesAfterManyOverwrites() {
        RecentLongSet set = new RecentLongSet(32);

        for (long i = 0; i < 1_000; i++) {
            assertThat(set.add(i)).isTrue();
        }
        for (long i = 968; i < 1_000; i++) {
            assertThat(set.add(i)).isFalse();
        }
        assertThat(set.add(967L)).isTrue();
        assertThat(set.size()).isEqualTo(32);
    }

    @Test
    void add_tracksLargeAndNegativeValuesInsideWindow() {
        RecentLongSet set = new RecentLongSet(3);

        assertThat(set.add(Long.MAX_VALUE)).isTrue();
        assertThat(set.add(Long.MAX_VALUE)).isFalse();
        assertThat(set.add(Long.MIN_VALUE)).isTrue();
        assertThat(set.add(Long.MIN_VALUE)).isFalse();
        assertThat(set.add(-1L)).isTrue();
        assertThat(set.add(-1L)).isFalse();
        assertThat(set.size()).isEqualTo(3);
    }

    @Test
    void clear_removesTrackedValuesAndReleasesStorage() {
        RecentLongSet set = new RecentLongSet(2);
        set.add(1L);
        set.add(2L);

        set.clear();

        assertThat(set.size()).isZero();
        assertThat(set.add(1L)).isTrue();
    }

    @Test
    void zeroCapacityDisablesDuplicateTracking() {
        RecentLongSet set = new RecentLongSet(0);

        assertThat(set.add(1L)).isTrue();
        assertThat(set.add(1L)).isTrue();
        assertThat(set.size()).isZero();
        assertThat(set.capacity()).isZero();
    }

    @Test
    void add_matchesRecentWindowReferenceAcrossRandomInput() {
        int capacity = 64;
        RecentLongSet set = new RecentLongSet(capacity);
        ArrayDeque<Long> window = new ArrayDeque<>();
        Set<Long> reference = new HashSet<>();
        Random random = new Random(1);

        for (int i = 0; i < 10_000; i++) {
            long value = random.nextInt(512) - 256L;
            boolean expected = !reference.contains(value);

            assertThat(set.add(value)).isEqualTo(expected);

            if (expected) {
                window.addLast(value);
                reference.add(value);
                if (window.size() > capacity) {
                    reference.remove(window.removeFirst());
                }
            }
            assertThat(set.size()).isLessThanOrEqualTo(capacity);
        }
    }
}
