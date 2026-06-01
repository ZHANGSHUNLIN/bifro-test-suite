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

import java.util.ArrayList;
import java.util.List;

public final class MqttFrameParser {
    
    public static final int TYPE_CONNECT = 1;
    public static final int TYPE_CONNACK = 2;
    public static final int TYPE_PUBLISH = 3;
    public static final int TYPE_PUBACK = 4;
    public static final int TYPE_PUBREC = 5;
    public static final int TYPE_PUBREL = 6;
    public static final int TYPE_PUBCOMP = 7;
    public static final int TYPE_SUBSCRIBE = 8;
    public static final int TYPE_SUBACK = 9;
    public static final int TYPE_UNSUBSCRIBE = 10;
    public static final int TYPE_UNSUBACK = 11;
    public static final int TYPE_PINGREQ = 12;
    public static final int TYPE_PINGRESP = 13;
    public static final int TYPE_DISCONNECT = 14;
    
    private final List<Frame> completed = new ArrayList<>();

    private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream(256);
    
    private int fixedHeader1 = -1;  
    private int remainingLength = -1;
    private int multiplier = 1;
    private int rlAccum = 0;
    private boolean readingRl = false;

    public void feed(byte[] data, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            int b = data[i] & 0xFF;
            if (fixedHeader1 < 0) {
                
                fixedHeader1 = b;
                readingRl = true;
                multiplier = 1;
                rlAccum = 0;
            } else if (readingRl) {
                
                rlAccum += (b & 0x7F) * multiplier;
                multiplier *= 128;
                if ((b & 0x80) == 0) {
                    
                    readingRl = false;
                    remainingLength = rlAccum;
                    if (remainingLength == 0) {
                        emitFrame();
                    }
                }
            } else {
                
                buf.write(b);
                if (buf.size() == remainingLength) {
                    emitFrame();
                }
            }
        }
    }

    private void emitFrame() {
        int type = (fixedHeader1 >> 4) & 0x0F;
        int flags = fixedHeader1 & 0x0F;
        byte[] payload = buf.toByteArray();
        completed.add(new Frame(type, flags, payload));
        
        fixedHeader1 = -1;
        remainingLength = -1;
        buf.reset();
    }
    
    public Frame poll() {
        if (completed.isEmpty()) {
            return null;
        }
        return completed.remove(0);
    }
    
    public int available() {
        return completed.size();
    }
    
    public void reset() {
        fixedHeader1 = -1;
        remainingLength = -1;
        readingRl = false;
        buf.reset();
        completed.clear();
    }
    
    public static class Frame {
        
        public final int type;
        
        public final int flags;
        
        public final byte[] payload;

        Frame(int type, int flags, byte[] payload) {
            this.type = type;
            this.flags = flags;
            this.payload = payload;
        }
        
        public int readUint16(int offset) {
            return ((payload[offset] & 0xFF) << 8) | (payload[offset + 1] & 0xFF);
        }

        public int connackReturnCode() {
            if (type != TYPE_CONNACK) {
                throw new IllegalStateException("Not a CONNACK frame: type=" + type);
            }
            return payload[1] & 0xFF; 
        }

        public int packetId() {
            if (payload.length < 2) {
                throw new IllegalStateException("Frame too short to contain packetId");
            }
            return readUint16(0);
        }
        
        public int publishQos() {
            return (flags >> 1) & 0x03;
        }
        
        public String publishTopic() {
            int topicLen = readUint16(0);
            return new String(payload, 2, topicLen, java.nio.charset.StandardCharsets.UTF_8);
        }
        
        public int publishPacketId() {
            int topicLen = readUint16(0);
            return readUint16(2 + topicLen);
        }

        @Override
        public String toString() {
            return "Frame{type=" + type + ", flags=0x" + Integer.toHexString(flags)
                + ", payloadLen=" + payload.length + "}";
        }
    }
}
