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

import {describe, expect, it} from 'vitest';
import {formatByteRate, formatByteSize} from '../byteFormat';

describe('byteFormat', () => {
    it('formatByteSize_scalesBy1024Boundaries', () => {
        expect(formatByteSize(undefined)).toBe('-');
        expect(formatByteSize(0)).toBe('0 B');
        expect(formatByteSize(1023)).toBe('1,023 B');
        expect(formatByteSize(1024)).toBe('1 KB');
        expect(formatByteSize(1536)).toBe('1.5 KB');
        expect(formatByteSize(1024 * 1024)).toBe('1 MB');
        expect(formatByteSize(1024 * 1024 * 1024)).toBe('1 GB');
    });

    it('formatByteRate_appendsPerSecondAfterScaledUnit', () => {
        expect(formatByteRate(undefined)).toBe('-');
        expect(formatByteRate(512)).toBe('512 B/s');
        expect(formatByteRate(1024)).toBe('1 KB/s');
        expect(formatByteRate(1024 * 1024 * 2.5)).toBe('2.5 MB/s');
    });
});
