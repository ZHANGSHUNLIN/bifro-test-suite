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

import java.nio.charset.StandardCharsets;

public final class MqttFrameEncoder {

    private MqttFrameEncoder() {
    }
    
    public static byte[] connect(String clientId, int keepAlive, boolean cleanSession) {
        byte[] clientIdBytes = utf8Bytes(clientId);

        int varHeaderLen = 10;
        
        int payloadLen = 2 + clientIdBytes.length;

        int remainingLength = varHeaderLen + payloadLen;
        byte[] frame = new byte[2 + remainingLength];

        int i = 0;
        frame[i++] = 0x10; 
        frame[i++] = (byte) remainingLength;
        
        frame[i++] = 0x00;
        frame[i++] = 0x04;
        frame[i++] = 'M';
        frame[i++] = 'Q';
        frame[i++] = 'T';
        frame[i++] = 'T';
        
        frame[i++] = 0x04;

        byte connectFlags = 0x00;
        if (cleanSession) {
            connectFlags |= 0x02;
        }
        frame[i++] = connectFlags;

        frame[i++] = (byte) ((keepAlive >> 8) & 0xFF);
        frame[i++] = (byte) (keepAlive & 0xFF);

        frame[i++] = (byte) ((clientIdBytes.length >> 8) & 0xFF);
        frame[i++] = (byte) (clientIdBytes.length & 0xFF);
        System.arraycopy(clientIdBytes, 0, frame, i, clientIdBytes.length);

        return frame;
    }
    
    public static byte[] publishQos0(String topic, byte[] payload) {
        byte[] topicBytes = utf8Bytes(topic);
        int remainingLength = 2 + topicBytes.length + payload.length;

        byte[] frame = new byte[2 + remainingLength];
        int i = 0;
        frame[i++] = 0x30; 
        frame[i++] = (byte) remainingLength;

        frame[i++] = (byte) ((topicBytes.length >> 8) & 0xFF);
        frame[i++] = (byte) (topicBytes.length & 0xFF);
        System.arraycopy(topicBytes, 0, frame, i, topicBytes.length);
        i += topicBytes.length;

        System.arraycopy(payload, 0, frame, i, payload.length);
        return frame;
    }
    
    public static byte[] publishQos1(String topic, byte[] payload, int packetId) {
        byte[] topicBytes = utf8Bytes(topic);
        
        int remainingLength = 2 + topicBytes.length + 2 + payload.length;

        byte[] frame = new byte[2 + remainingLength];
        int i = 0;
        frame[i++] = 0x32; 
        frame[i++] = (byte) remainingLength;

        frame[i++] = (byte) ((topicBytes.length >> 8) & 0xFF);
        frame[i++] = (byte) (topicBytes.length & 0xFF);
        System.arraycopy(topicBytes, 0, frame, i, topicBytes.length);
        i += topicBytes.length;

        frame[i++] = (byte) ((packetId >> 8) & 0xFF);
        frame[i++] = (byte) (packetId & 0xFF);

        System.arraycopy(payload, 0, frame, i, payload.length);
        return frame;
    }
    
    public static byte[] puback(int packetId) {
        return new byte[] {
            0x40,
            0x02,
            (byte) ((packetId >> 8) & 0xFF),
            (byte) (packetId & 0xFF)
        };
    }
    
    public static byte[] subscribe(String topicFilter, int packetId) {
        byte[] filterBytes = utf8Bytes(topicFilter);
        
        int remainingLength = 2 + 2 + filterBytes.length + 1;

        byte[] frame = new byte[2 + remainingLength];
        int i = 0;
        frame[i++] = (byte) 0x82; 
        frame[i++] = (byte) remainingLength;
        
        frame[i++] = (byte) ((packetId >> 8) & 0xFF);
        frame[i++] = (byte) (packetId & 0xFF);

        frame[i++] = (byte) ((filterBytes.length >> 8) & 0xFF);
        frame[i++] = (byte) (filterBytes.length & 0xFF);
        System.arraycopy(filterBytes, 0, frame, i, filterBytes.length);
        i += filterBytes.length;

        frame[i] = 0x01;
        return frame;
    }
    
    public static byte[] disconnect() {
        return new byte[] {(byte) 0xE0, 0x00};
    }

    public static byte[] pingReq() {
        return new byte[] {(byte) 0xC0, 0x00};
    }

    private static byte[] utf8Bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
