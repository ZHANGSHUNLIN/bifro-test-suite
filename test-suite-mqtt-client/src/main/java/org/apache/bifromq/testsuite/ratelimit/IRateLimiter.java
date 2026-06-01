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

package org.apache.bifromq.testsuite.ratelimit;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface IRateLimiter {
    static IRateLimiter create(int permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive: " + permitsPerSecond);
        }
        return new TokenBucketRateLimiter(permitsPerSecond);
    }

    int getPermitsPerSecond();

    long getIntervalNanos();

    long getAcquiredCount();

    long getFailedCount();

    void resetMetrics();

    long getTotalWaitNanos();

    void setRate(int permitsPerSecond);

    void dispose();

    CompletableFuture<Void> executeWithRateLimit(int total,
                                                 Function<Integer, CompletableFuture<Void>> action);
}
