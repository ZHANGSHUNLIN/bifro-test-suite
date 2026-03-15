/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.utils;

import java.util.concurrent.atomic.AtomicLong;

public class PayloadUtils {

    private static final AtomicLong index = new AtomicLong();

    public static long genIndex() {
        return index.incrementAndGet();
    }

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
}
