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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.bifromq.testsuite.chaos.ChaosBehavior.BrokerReaction;
import org.apache.bifromq.testsuite.chaos.ChaosContext;
import org.apache.bifromq.testsuite.chaos.MqttFrameParser;
import org.apache.bifromq.testsuite.chaos.RawMqttConnection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChaosBehaviorTest {

    private RawMqttConnection conn;

    @BeforeEach
    void setUp() {
        conn = mock(RawMqttConnection.class);
        when(conn.sendRaw(any())).thenReturn(CompletableFuture.completedFuture(null));
        
    }

    private ChaosContext defaultCtx() {
        return ChaosContext.builder()
            .connection(conn)
            .startPacketId(42)
            .topic("test/chaos")
            .maxInflightWindow(10)
            .maxPacketSize(65536)
            .build();
    }

    
    
    

    @Test
    void duplicatePuback_sendsTwoPubacksWithSameBytes() throws Exception {
        DuplicatePubackBehavior behavior = new DuplicatePubackBehavior();
        behavior.execute(defaultCtx()); 

        
        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(conn, times(2)).sendRaw(captor.capture());

        byte[] first = captor.getAllValues().get(0);
        byte[] second = captor.getAllValues().get(1);
        assertThat(first).containsExactly(second);

        
        assertThat(first[0] & 0xFF).isEqualTo(0x40);
    }

    @Test
    void duplicatePuback_name() {
        assertThat(new DuplicatePubackBehavior().name()).isEqualTo("DUPLICATE_PUBACK");
    }

    
    
    

    @Test
    void exceedInflightWindow_sendsNPlus1Publishes() throws Exception {
        ExceedInflightWindowBehavior behavior = new ExceedInflightWindowBehavior();
        behavior.execute(defaultCtx());

        
        verify(conn, times(11)).sendRaw(any());
    }

    @Test
    void exceedInflightWindow_allPacketIdsDifferent() throws Exception {
        ExceedInflightWindowBehavior behavior = new ExceedInflightWindowBehavior();
        ChaosContext ctx = ChaosContext.builder()
            .connection(conn).startPacketId(1).topic("t").maxInflightWindow(5).maxPacketSize(1024).build();
        behavior.execute(ctx);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(conn, times(6)).sendRaw(captor.capture());

        
        java.util.Set<Integer> packetIds = new java.util.HashSet<>();
        for (byte[] frame : captor.getAllValues()) {
            
            byte[] topic = "t".getBytes();
            int pidOffset = 2 + 2 + topic.length;
            int pid = ((frame[pidOffset] & 0xFF) << 8) | (frame[pidOffset + 1] & 0xFF);
            packetIds.add(pid);
        }
        assertThat(packetIds).hasSize(6); 
    }

    @Test
    void exceedInflightWindow_name() {
        assertThat(new ExceedInflightWindowBehavior().name()).isEqualTo("EXCEED_INFLIGHT_WINDOW");
    }

    
    
    

    @Test
    void doubleConnect_sendsConnectFrameOnce() throws Exception {
        DoubleConnectBehavior behavior = new DoubleConnectBehavior();
        behavior.execute(defaultCtx());

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(conn, times(1)).sendRaw(captor.capture());

        
        assertThat(captor.getValue()[0] & 0xFF).isEqualTo(0x10);
    }

    @Test
    void doubleConnect_name() {
        assertThat(new DoubleConnectBehavior().name()).isEqualTo("DOUBLE_CONNECT");
    }

    
    
    

    @Test
    void invalidPacketIdZero_publishWithPacketIdZero() throws Exception {
        InvalidPacketIdZeroBehavior behavior = new InvalidPacketIdZeroBehavior();
        behavior.execute(defaultCtx());

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(conn, times(1)).sendRaw(captor.capture());

        byte[] frame = captor.getValue();
        
        assertThat(frame[0] & 0xFF).isEqualTo(0x32);
        
        byte[] topic = "test/chaos".getBytes();
        int pidOffset = 2 + 2 + topic.length;
        int packetId = ((frame[pidOffset] & 0xFF) << 8) | (frame[pidOffset + 1] & 0xFF);
        assertThat(packetId).isEqualTo(0); 
    }

    @Test
    void invalidPacketIdZero_name() {
        assertThat(new InvalidPacketIdZeroBehavior().name()).isEqualTo("INVALID_PACKET_ID_ZERO");
    }

    
    
    

    @Test
    void oversizedPayload_frameSizeExceedsMaxPacketSize() throws Exception {
        int maxPacketSize = 128;
        ChaosContext ctx = ChaosContext.builder()
            .connection(conn).startPacketId(1).topic("t").maxInflightWindow(10)
            .maxPacketSize(maxPacketSize).build();

        OversizedPayloadBehavior behavior = new OversizedPayloadBehavior();
        behavior.execute(ctx);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(conn, times(1)).sendRaw(captor.capture());

        
        assertThat(captor.getValue().length).isGreaterThan(maxPacketSize);
    }

    @Test
    void oversizedPayload_name() {
        assertThat(new OversizedPayloadBehavior().name()).isEqualTo("OVERSIZED_PAYLOAD");
    }

    
    
    

    @Test
    void malformedTopic_publishWithHashInTopic() throws Exception {
        MalformedTopicBehavior behavior = new MalformedTopicBehavior();
        behavior.execute(defaultCtx());

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(conn, times(1)).sendRaw(captor.capture());

        byte[] frame = captor.getValue();
        
        int topicLen = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        String topic = new String(frame, 4, topicLen, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(topic).contains("#");
    }

    @Test
    void malformedTopic_name() {
        assertThat(new MalformedTopicBehavior().name()).isEqualTo("MALFORMED_TOPIC");
    }

    
    
    

    @Test
    void brokerSendsDisconnect_reactionIsDisconnect() throws Exception {
        
        ArgumentCaptor<Consumer> frameCaptor = ArgumentCaptor.forClass(Consumer.class);

        DuplicatePubackBehavior behavior = new DuplicatePubackBehavior();
        CompletableFuture<BrokerReaction> future = behavior.execute(defaultCtx());

        verify(conn, atLeast(1)).onFrame(frameCaptor.capture());
        Consumer<MqttFrameParser.Frame> handler = frameCaptor.getValue();

        
        MqttFrameParser parser = new MqttFrameParser();
        byte[] disconnectBytes = org.apache.bifromq.testsuite.chaos.MqttFrameEncoder.disconnect();
        parser.feed(disconnectBytes, 0, disconnectBytes.length);
        handler.accept(parser.poll());

        BrokerReaction reaction = future.get(1, TimeUnit.SECONDS);
        assertThat(reaction).isEqualTo(BrokerReaction.DISCONNECT);
    }

    
    
    

    @Test
    void tcpClose_graceful_reactionIsDisconnect() throws Exception {
        ArgumentCaptor<Consumer> closeCaptor = ArgumentCaptor.forClass(Consumer.class);

        DuplicatePubackBehavior behavior = new DuplicatePubackBehavior();
        CompletableFuture<BrokerReaction> future = behavior.execute(defaultCtx());

        verify(conn, atLeast(1)).onClose(closeCaptor.capture());
        
        closeCaptor.getValue().accept(null);

        BrokerReaction reaction = future.get(1, TimeUnit.SECONDS);
        assertThat(reaction).isEqualTo(BrokerReaction.DISCONNECT);
    }

    @Test
    void tcpClose_withException_reactionIsTcpReset() throws Exception {
        ArgumentCaptor<Consumer> closeCaptor = ArgumentCaptor.forClass(Consumer.class);

        DuplicatePubackBehavior behavior = new DuplicatePubackBehavior();
        CompletableFuture<BrokerReaction> future = behavior.execute(defaultCtx());

        verify(conn, atLeast(1)).onClose(closeCaptor.capture());
        
        closeCaptor.getValue().accept(new java.io.IOException("Connection reset by peer"));

        BrokerReaction reaction = future.get(1, TimeUnit.SECONDS);
        assertThat(reaction).isEqualTo(BrokerReaction.TCP_RESET);
    }
}
