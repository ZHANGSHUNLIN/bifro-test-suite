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

package org.apache.bifromq.testsuite.utils;

public class PayloadUtils {

    public static void attachTimeAndIndex(byte[] payload, long startTime, long index) {
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            payload[i] = (byte) (startTime & 0xFF);
            startTime >>>= Byte.SIZE;
        }
        for (int i = 2 * Long.BYTES - 1; i >= Long.BYTES; i--) {
            payload[i] = (byte) (index & 0xFF);
            index >>>= Byte.SIZE;
        }
    }

    public static long extractTimestamp(byte[] payload) {
        long timestamp = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            timestamp <<= Byte.SIZE;
            timestamp |= (payload[i] & 0xFF);
        }
        return timestamp;
    }

    public static long extractIndex(byte[] payload) {
        long index = 0;
        for (int i = Long.BYTES; i <= Long.BYTES * 2 - 1; i++) {
            index <<= Byte.SIZE;
            index |= (payload[i] & 0xFF);
        }
        return index;
    }

    
    public static boolean isBifroPayload(byte[] payload) {
        return payload != null && payload.length >= Long.BYTES * 2;
    }
}
