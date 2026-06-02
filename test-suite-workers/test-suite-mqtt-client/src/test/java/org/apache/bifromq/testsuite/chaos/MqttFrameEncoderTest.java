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

package org.apache.bifromq.testsuite.chaos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MqttFrameEncoderTest {

    
    
    

    @Test
    void connect_firstByteIsConnectFixedHeader() {
        byte[] frame = MqttFrameEncoder.connect("test-001", 60, true);
        assertThat(frame[0] & 0xFF).isEqualTo(0x10);
    }

    @Test
    void connect_protocolNameIsMQTT() {
        byte[] frame = MqttFrameEncoder.connect("test-001", 60, true);
        
        assertThat((char) frame[4]).isEqualTo('M');
        assertThat((char) frame[5]).isEqualTo('Q');
        assertThat((char) frame[6]).isEqualTo('T');
        assertThat((char) frame[7]).isEqualTo('T');
    }

    @Test
    void connect_protocolLevelIs4ForMqtt311() {
        byte[] frame = MqttFrameEncoder.connect("test-001", 60, true);
        
        assertThat(frame[8] & 0xFF).isEqualTo(0x04);
    }

    @Test
    void connect_cleanSessionFlag_setBit1() {
        byte[] cleanFrame = MqttFrameEncoder.connect("cid", 30, true);
        byte[] dirtyFrame = MqttFrameEncoder.connect("cid", 30, false);
        
        assertThat(cleanFrame[9] & 0x02).isEqualTo(0x02);
        assertThat(dirtyFrame[9] & 0x02).isEqualTo(0x00);
    }

    @Test
    void connect_keepAliveEncodedBigEndian() {
        byte[] frame = MqttFrameEncoder.connect("c", 256, false);
        
        int keepAlive = ((frame[10] & 0xFF) << 8) | (frame[11] & 0xFF);
        assertThat(keepAlive).isEqualTo(256);
    }

    
    
    

    @Test
    void publish_qos1_fixedHeaderIs0x32() {
        byte[] frame = MqttFrameEncoder.publishQos1("test/t", new byte[] {0x01}, 42);
        assertThat(frame[0] & 0xFF).isEqualTo(0x32);
    }

    @Test
    void publish_qos1_packetIdEncodedBigEndian() {
        byte[] topic = "t".getBytes();
        int packetId = 42; 
        byte[] frame = MqttFrameEncoder.publishQos1("t", new byte[] {0x01, 0x02}, packetId);
        
        int pidOffset = 2 + 2 + topic.length;
        int decodedPid = ((frame[pidOffset] & 0xFF) << 8) | (frame[pidOffset + 1] & 0xFF);
        assertThat(decodedPid).isEqualTo(42);
    }

    @Test
    void publish_qos1_allowsPacketIdZero_noException() {
        
        byte[] frame = MqttFrameEncoder.publishQos1("test/topic", new byte[0], 0);
        assertThat(frame).isNotNull();
        byte[] topic = "test/topic".getBytes();
        int pidOffset = 2 + 2 + topic.length;
        int decodedPid = ((frame[pidOffset] & 0xFF) << 8) | (frame[pidOffset + 1] & 0xFF);
        assertThat(decodedPid).isEqualTo(0);
    }

    @Test
    void publish_qos0_fixedHeaderIs0x30() {
        byte[] frame = MqttFrameEncoder.publishQos0("t/t", new byte[] {});
        assertThat(frame[0] & 0xFF).isEqualTo(0x30);
    }

    
    
    

    @Test
    void puback_packetId42_returns4ByteSequence() {
        byte[] puback = MqttFrameEncoder.puback(42);
        assertThat(puback).hasSize(4);
        assertThat(puback[0] & 0xFF).isEqualTo(0x40);
        assertThat(puback[1] & 0xFF).isEqualTo(0x02);
        assertThat(puback[2] & 0xFF).isEqualTo(0x00);
        assertThat(puback[3] & 0xFF).isEqualTo(0x2A); 
    }

    @Test
    void puback_maxPacketId_65535() {
        byte[] puback = MqttFrameEncoder.puback(0xFFFF);
        assertThat(puback[2] & 0xFF).isEqualTo(0xFF);
        assertThat(puback[3] & 0xFF).isEqualTo(0xFF);
    }

    
    
    

    @Test
    void disconnect_returns2Bytes_0xE0_0x00() {
        byte[] frame = MqttFrameEncoder.disconnect();
        assertThat(frame).containsExactly((byte) 0xE0, (byte) 0x00);
    }

    
    
    

    @Test
    void subscribe_fixedHeaderIs0x82() {
        byte[] frame = MqttFrameEncoder.subscribe("test/#", 1);
        assertThat(frame[0] & 0xFF).isEqualTo(0x82);
    }
}
