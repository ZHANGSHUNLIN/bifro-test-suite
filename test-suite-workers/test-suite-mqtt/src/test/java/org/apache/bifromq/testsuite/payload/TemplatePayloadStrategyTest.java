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

package org.apache.bifromq.testsuite.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.apache.bifromq.testsuite.utils.PayloadUtils;
import org.junit.jupiter.api.Test;

class TemplatePayloadStrategyTest {

    

    @Test
    void literalOnly_returnsLiteralBytes() {
        TemplatePayloadStrategy s = new TemplatePayloadStrategy("hello world");
        byte[] result = s.buildPayload(0, 256);
        assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo("hello world");
    }

    @Test
    void indexPlaceholder_substitutedCorrectly() {
        TemplatePayloadStrategy s = new TemplatePayloadStrategy("msg-{{index}}");
        assertThat(new String(s.buildPayload(0, 128), StandardCharsets.UTF_8)).isEqualTo("msg-0");
        assertThat(new String(s.buildPayload(42, 128), StandardCharsets.UTF_8)).isEqualTo("msg-42");
    }

    @Test
    void timestampMsPlaceholder_isNumericString() {
        TemplatePayloadStrategy s = new TemplatePayloadStrategy("{{timestamp_ms}}");
        String result = new String(s.buildPayload(0, 128), StandardCharsets.UTF_8);
        assertThat(Long.parseLong(result)).isGreaterThan(0);
    }

    @Test
    void uuidPlaceholder_isValidUuidString() {
        TemplatePayloadStrategy s = new TemplatePayloadStrategy("{{uuid}}");
        String result = new String(s.buildPayload(0, 128), StandardCharsets.UTF_8);
        
        assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void randomTextPlaceholder_correctLength() {
        TemplatePayloadStrategy s = new TemplatePayloadStrategy("{{random_text:8}}");
        String result = new String(s.buildPayload(0, 128), StandardCharsets.UTF_8);
        assertThat(result).hasSize(8).matches("[a-z0-9]{8}");
    }

    @Test
    void randomIntPlaceholder_withinRange() {
        TemplatePayloadStrategy s = new TemplatePayloadStrategy("{{random_int:1:5}}");
        for (int i = 0; i < 50; i++) {
            String result = new String(s.buildPayload(i, 128), StandardCharsets.UTF_8);
            int value = Integer.parseInt(result);
            assertThat(value).isBetween(1, 5);
        }
    }

    @Test
    void multiPlaceholder_allExpanded() {
        TemplatePayloadStrategy s = new TemplatePayloadStrategy("1{{index}},{{timestamp_ms}}");
        String result = new String(s.buildPayload(3, 128), StandardCharsets.UTF_8);
        
        assertThat(result).startsWith("13,");
        long ts = Long.parseLong(result.substring(3));
        assertThat(ts).isGreaterThan(0);
    }

    

    @Test
    void buildPayload_targetSizeIgnored_noNullBytePadding() {
        
        TemplatePayloadStrategy s = new TemplatePayloadStrategy("hi");
        byte[] result = s.buildPayload(0, 256);
        assertThat(result).isEqualTo("hi".getBytes(StandardCharsets.UTF_8));
        assertThat(result).hasSize(2);
    }

    @Test
    void buildPayload_expandedLongerThanTargetSize_notTruncated() {
        
        TemplatePayloadStrategy s = new TemplatePayloadStrategy("hello");
        byte[] result = s.buildPayload(0, 2);
        assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    

    @Test
    void supportsLatencyTracking_returnsFalse() {
        assertThat(new TemplatePayloadStrategy("x").supportsLatencyTracking()).isFalse();
    }

    @Test
    void buildPayload_longTemplateText_matchesLegacyLengthCheckButDoesNotSupportLatencyTracking() {
        TemplatePayloadStrategy s = new TemplatePayloadStrategy("{\"ts\":{{timestamp_ms}},\"id\":{{index}}}");
        byte[] result = s.buildPayload(42, 128);
        assertThat(result.length).isGreaterThanOrEqualTo(Long.BYTES * 2);
        assertThat(s.supportsLatencyTracking()).isFalse();
        assertThat(PayloadUtils.isBifroPayload(result)).isTrue();
    }

    

    @Test
    void unknownPlaceholder_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new TemplatePayloadStrategy("{{foobar}}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown placeholder");
    }

    @Test
    void unclosedPlaceholder_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new TemplatePayloadStrategy("{{index"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unclosed placeholder");
    }

    @Test
    void randomTextZeroLength_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new TemplatePayloadStrategy("{{random_text:0}}"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void randomIntMinGreaterThanMax_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new TemplatePayloadStrategy("{{random_int:10:5}}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("min");
    }

    @Test
    void validateTemplate_validTemplate_doesNotThrow() {
        TemplatePayloadStrategy.validateTemplate("{{index}}-{{timestamp_ms}}");
    }

    @Test
    void validateTemplate_nullTemplate_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> TemplatePayloadStrategy.validateTemplate(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
