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

import org.apache.bifromq.testsuite.chaos.ChaosContext;
import org.apache.bifromq.testsuite.chaos.MqttFrameEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ExceedInflightWindowBehavior extends AbstractChaosBehavior {

    private static final byte[] SMALL_PAYLOAD = new byte[] {0x01};

    @Override
    public String name() {
        return "EXCEED_INFLIGHT_WINDOW";
    }

    @Override
    protected CompletableFuture<Void> sendViolation(ChaosContext ctx) {
        int count = ctx.maxInflightWindow() + 1;
        int basePacketId = ctx.startPacketId();
        
        List<CompletableFuture<Void>> sends = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int packetId = ((basePacketId + i - 1) % 65535) + 1; 
            byte[] publish = MqttFrameEncoder.publishQos1(ctx.topic(), SMALL_PAYLOAD, packetId);
            sends.add(ctx.connection().sendRaw(publish));
        }

        return CompletableFuture.allOf(sends.toArray(new CompletableFuture[0]));
    }
}
