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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MqttFrameParserTest {

    private MqttFrameParser parser;

    @BeforeEach
    void setUp() {
        parser = new MqttFrameParser();
    }

    
    
    

    @Test
    void parse_connackBytes_returnsConnackFrame() {
        
        byte[] connack = {0x20, 0x02, 0x00, 0x00};
        parser.feed(connack, 0, connack.length);

        MqttFrameParser.Frame frame = parser.poll();
        assertThat(frame).isNotNull();
        assertThat(frame.type).isEqualTo(MqttFrameParser.TYPE_CONNACK);
        assertThat(frame.connackReturnCode()).isEqualTo(0);
    }

    @Test
    void parse_connackRefused_returnsNonZeroCode() {
        
        byte[] connack = {0x20, 0x02, 0x00, 0x04};
        parser.feed(connack, 0, connack.length);

        MqttFrameParser.Frame frame = parser.poll();
        assertThat(frame.connackReturnCode()).isEqualTo(4);
    }

    
    
    

    @Test
    void parse_pubackBytes_returnsPacketId() {
        byte[] puback = MqttFrameEncoder.puback(42);
        parser.feed(puback, 0, puback.length);

        MqttFrameParser.Frame frame = parser.poll();
        assertThat(frame).isNotNull();
        assertThat(frame.type).isEqualTo(MqttFrameParser.TYPE_PUBACK);
        assertThat(frame.packetId()).isEqualTo(42);
    }

    
    
    

    @Test
    void parse_disconnectBytes_returnsDisconnectType() {
        byte[] disconnect = MqttFrameEncoder.disconnect();
        parser.feed(disconnect, 0, disconnect.length);

        MqttFrameParser.Frame frame = parser.poll();
        assertThat(frame).isNotNull();
        assertThat(frame.type).isEqualTo(MqttFrameParser.TYPE_DISCONNECT);
    }

    
    
    

    @Test
    void parse_splitBytes_connack_reassembledCorrectly() {
        byte[] connack = {0x20, 0x02, 0x00, 0x00};
        
        for (byte b : connack) {
            parser.feed(new byte[] {b}, 0, 1);
            if (parser.available() == 0) {
                
            }
        }
        assertThat(parser.available()).isEqualTo(1);
        assertThat(parser.poll().type).isEqualTo(MqttFrameParser.TYPE_CONNACK);
    }

    
    
    

    @Test
    void parse_multipleFrames_allParsedInOrder() {
        
        byte[] puback1 = MqttFrameEncoder.puback(1);
        byte[] puback2 = MqttFrameEncoder.puback(2);
        byte[] combined = new byte[puback1.length + puback2.length];
        System.arraycopy(puback1, 0, combined, 0, puback1.length);
        System.arraycopy(puback2, 0, combined, puback1.length, puback2.length);

        parser.feed(combined, 0, combined.length);

        assertThat(parser.available()).isEqualTo(2);
        assertThat(parser.poll().packetId()).isEqualTo(1);
        assertThat(parser.poll().packetId()).isEqualTo(2);
    }

    
    
    

    @Test
    void parse_publishQos1_topicAndPacketIdCorrect() {
        byte[] frame = MqttFrameEncoder.publishQos1("test/chaos", new byte[] {0x01}, 99);
        parser.feed(frame, 0, frame.length);

        MqttFrameParser.Frame parsed = parser.poll();
        assertThat(parsed.type).isEqualTo(MqttFrameParser.TYPE_PUBLISH);
        assertThat(parsed.publishQos()).isEqualTo(1);
        assertThat(parsed.publishTopic()).isEqualTo("test/chaos");
        assertThat(parsed.publishPacketId()).isEqualTo(99);
    }

    
    
    

    @Test
    void reset_clearsAllState() {
        byte[] puback = MqttFrameEncoder.puback(1);
        parser.feed(puback, 0, puback.length);
        assertThat(parser.available()).isEqualTo(1);

        parser.reset();
        assertThat(parser.available()).isEqualTo(0);
        assertThat(parser.poll()).isNull();
    }

    
    
    

    @Test
    void poll_emptyQueue_returnsNull() {
        assertThat(parser.poll()).isNull();
    }

    
    
    

    @Test
    void connackReturnCode_onNonConnackFrame_throws() {
        byte[] puback = MqttFrameEncoder.puback(1);
        parser.feed(puback, 0, puback.length);
        MqttFrameParser.Frame frame = parser.poll();

        assertThatThrownBy(frame::connackReturnCode)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Not a CONNACK frame");
    }
}
