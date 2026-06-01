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

package org.apache.bifromq.testsuite;

final class RecentLongSet {
    static final int DEFAULT_CAPACITY = Integer.getInteger("bifro.sub.dedup.capacity", 32);

    private static final int EMPTY = -1;
    private static final int DEFAULT_TABLE_SIZE = 16;
    private static final float LOAD_FACTOR = 0.65f;

    private final int capacity;
    private long[] ring;
    private int[] ringToSlot;
    private long[] keys;
    private int[] slots;
    private int size;
    private int nextIndex;
    private int resizeThreshold;

    RecentLongSet() {
        this(DEFAULT_CAPACITY);
    }

    RecentLongSet(int capacity) {
        this.capacity = Math.max(0, capacity);
    }

    synchronized boolean add(long value) {
        if (capacity == 0) {
            return true;
        }
        if (ring == null) {
            allocateRing();
            allocateTable(tableSizeFor(capacity));
        }

        int existingSlot = findSlot(value);
        if (slots[existingSlot] != EMPTY) {
            return false;
        }

        if (size == capacity) {
            removeRingEntry(nextIndex);
        } else if (size + 1 > resizeThreshold) {
            resize(keys.length << 1);
        }

        int slot = findSlot(value);
        ring[nextIndex] = value;
        ringToSlot[nextIndex] = slot;
        keys[slot] = value;
        slots[slot] = nextIndex;
        nextIndex = (nextIndex + 1) % capacity;
        if (size < capacity) {
            size++;
        }
        return true;
    }

    synchronized void clear() {
        ring = null;
        ringToSlot = null;
        keys = null;
        slots = null;
        size = 0;
        nextIndex = 0;
        resizeThreshold = 0;
    }

    synchronized int size() {
        return size;
    }

    int capacity() {
        return capacity;
    }

    private void allocateRing() {
        ring = new long[capacity];
        ringToSlot = new int[capacity];
        fill(ringToSlot);
    }

    private void allocateTable(int tableSize) {
        keys = new long[tableSize];
        slots = new int[tableSize];
        fill(slots);
        resizeThreshold = Math.max(1, (int) (tableSize * LOAD_FACTOR));
    }

    private void resize(int tableSize) {
        allocateTable(tableSize);
        int count = size;
        int start = (nextIndex - count + capacity) % capacity;
        for (int i = 0; i < count; i++) {
            int ringIndex = (start + i) % capacity;
            long value = ring[ringIndex];
            int slot = findSlot(value);
            keys[slot] = value;
            slots[slot] = ringIndex;
            ringToSlot[ringIndex] = slot;
        }
    }

    private void removeRingEntry(int ringIndex) {
        int slot = ringToSlot[ringIndex];
        if (slot == EMPTY) {
            return;
        }

        slots[slot] = EMPTY;
        ringToSlot[ringIndex] = EMPTY;
        closeDeletionGap(slot);
    }

    private void closeDeletionGap(int deletedSlot) {
        int mask = keys.length - 1;
        int gap = deletedSlot;
        int current = (gap + 1) & mask;
        while (slots[current] != EMPTY) {
            int ideal = mix(keys[current]) & mask;
            if (mustMove(ideal, gap, current, mask)) {
                keys[gap] = keys[current];
                slots[gap] = slots[current];
                ringToSlot[slots[gap]] = gap;
                slots[current] = EMPTY;
                gap = current;
            }
            current = (current + 1) & mask;
        }
    }

    private int findSlot(long value) {
        int mask = keys.length - 1;
        int index = mix(value) & mask;
        while (slots[index] != EMPTY && keys[index] != value) {
            index = (index + 1) & mask;
        }
        return index;
    }

    private static boolean mustMove(int ideal, int gap, int current, int mask) {
        return ((current - ideal) & mask) >= ((gap - ideal) & mask);
    }

    private static int tableSizeFor(int capacity) {
        int needed = Math.max(DEFAULT_TABLE_SIZE, (int) Math.ceil(capacity / LOAD_FACTOR) + 1);
        int tableSize = 1;
        while (tableSize < needed) {
            tableSize <<= 1;
        }
        return tableSize;
    }

    private static void fill(int[] values) {
        for (int i = 0; i < values.length; i++) {
            values[i] = EMPTY;
        }
    }

    private static int mix(long value) {
        long h = value;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (int) h;
    }
}
