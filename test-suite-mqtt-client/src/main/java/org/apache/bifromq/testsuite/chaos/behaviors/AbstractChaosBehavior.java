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

package org.apache.bifromq.testsuite.chaos.behaviors;

import org.apache.bifromq.testsuite.chaos.ChaosBehavior;
import org.apache.bifromq.testsuite.chaos.ChaosContext;
import org.apache.bifromq.testsuite.chaos.MqttFrameParser;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class AbstractChaosBehavior implements ChaosBehavior {

    protected static final long OBSERVATION_TIMEOUT_MS = 5_000L;

    private static final ScheduledExecutorService SCHEDULER = new ScheduledThreadPoolExecutor(1,
        r -> {
            Thread t = new Thread(r, "chaos-observation-timer");
            t.setDaemon(true);
            return t;
        });

    private static void complete(CompletableFuture<BrokerReaction> future,
                                 AtomicBoolean done,
                                 BrokerReaction reaction) {
        if (done.compareAndSet(false, true)) {
            future.complete(reaction);
        }
    }
    
    @Override
    public final CompletableFuture<BrokerReaction> execute(ChaosContext ctx) {
        CompletableFuture<BrokerReaction> reactionFuture = new CompletableFuture<>();
        AtomicBoolean done = new AtomicBoolean(false);

        ctx.connection().onFrame(frame -> {
            if (frame.type == MqttFrameParser.TYPE_DISCONNECT) {
                complete(reactionFuture, done, BrokerReaction.DISCONNECT);
            } else if (shouldTriggerReaction(frame)) {
                complete(reactionFuture, done, extraReaction(frame));
            }
        });

        ctx.connection().onClose(err -> {
            if (err != null) {
                complete(reactionFuture, done, BrokerReaction.TCP_RESET);
            } else {
                
                complete(reactionFuture, done, BrokerReaction.DISCONNECT);
            }
        });

        sendViolation(ctx).whenComplete((v, ex) -> {
            if (ex != null) {
                
                complete(reactionFuture, done, BrokerReaction.TCP_RESET);
                return;
            }
            
            ScheduledFuture<?> timeout = SCHEDULER.schedule(
                () -> complete(reactionFuture, done, BrokerReaction.NO_RESPONSE),
                OBSERVATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            reactionFuture.whenComplete((r, e) -> timeout.cancel(false));
        });

        return reactionFuture;
    }
    
    protected abstract CompletableFuture<Void> sendViolation(ChaosContext ctx);
    
    protected boolean shouldTriggerReaction(MqttFrameParser.Frame frame) {
        return false;
    }

    protected BrokerReaction extraReaction(MqttFrameParser.Frame frame) {
        return BrokerReaction.NO_RESPONSE;
    }
}
