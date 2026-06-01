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

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class OversizedPayloadBehavior extends AbstractChaosBehavior {

    @Override
    public String name() {
        return "OVERSIZED_PAYLOAD";
    }

    @Override
    protected CompletableFuture<Void> sendViolation(ChaosContext ctx) {
        byte[] topicBytes = ctx.topic().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        int overhead = 2 + 2 + topicBytes.length + 2;
        int payloadSize = ctx.maxPacketSize() - overhead + 1; 
        if (payloadSize <= 0) {
            payloadSize = 1;
        }

        byte[] payload = new byte[payloadSize];
        Arrays.fill(payload, (byte) 0x41);

        int packetId = ctx.startPacketId();
        byte[] frame = MqttFrameEncoder.publishQos1(ctx.topic(), payload, packetId);
        return ctx.connection().sendRaw(frame);
    }
}
