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

const BYTE_UNIT_BASE = 1024;
const BYTE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];

const trimFixed = (value: number, digits: number): string => {
    return Number(value.toFixed(digits)).toLocaleString(undefined, {
        maximumFractionDigits: digits,
    });
};

export const formatByteSize = (value?: number, digits = 2): string => {
    if (value == null || !Number.isFinite(value)) {
        return '-';
    }
    if (value === 0) {
        return '0 B';
    }

    const absValue = Math.abs(value);
    const unitIndex = Math.min(
        Math.floor(Math.log(absValue) / Math.log(BYTE_UNIT_BASE)),
        BYTE_UNITS.length - 1
    );
    const scaledValue = value / Math.pow(BYTE_UNIT_BASE, unitIndex);
    const fractionDigits = unitIndex === 0 ? 0 : digits;
    return `${trimFixed(scaledValue, fractionDigits)} ${BYTE_UNITS[unitIndex]}`;
};

export const formatByteRate = (value?: number, digits = 2): string => {
    const formatted = formatByteSize(value, digits);
    return formatted === '-' ? '-' : `${formatted}/s`;
};
