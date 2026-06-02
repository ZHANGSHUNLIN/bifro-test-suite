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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WillConfigTest {

    @Test
    void testDefaultConstructor() {
        WillConfig config = new WillConfig();

        assertThat(config.getWillFlag()).isEqualTo(false);
        assertThat(config.getWillTopic()).isNull();
        assertThat(config.getWillMessage()).isNull();
        assertThat(config.getWillMessageLen()).isNull();
        assertThat(config.getWillQos()).isNull();
        assertThat(config.getWillRetain()).isNull();
    }

    @Test
    void testAllArgsConstructor() {
        WillConfig config = new WillConfig(
            true,
            "topic/test",
            "message content",
            10,
            1,
            true
        );

        assertThat(config.getWillFlag()).isTrue();
        assertThat(config.getWillTopic()).isEqualTo("topic/test");
        assertThat(config.getWillMessage()).isEqualTo("message content");
        assertThat(config.getWillMessageLen()).isEqualTo(10);
        assertThat(config.getWillQos()).isEqualTo(1);
        assertThat(config.getWillRetain()).isTrue();
    }

    @Test
    void testSetters() {
        WillConfig config = new WillConfig();

        config.setWillFlag(true);
        config.setWillTopic("topic/test");
        config.setWillMessage("message");
        config.setWillMessageLen(20);
        config.setWillQos(2);
        config.setWillRetain(false);

        assertThat(config.getWillFlag()).isTrue();
        assertThat(config.getWillTopic()).isEqualTo("topic/test");
        assertThat(config.getWillMessage()).isEqualTo("message");
        assertThat(config.getWillMessageLen()).isEqualTo(20);
        assertThat(config.getWillQos()).isEqualTo(2);
        assertThat(config.getWillRetain()).isFalse();
    }

    @Test
    void testEquals() {
        WillConfig config1 = new WillConfig(true, "topic", "msg", 10, 1, true);
        WillConfig config2 = new WillConfig(true, "topic", "msg", 10, 1, true);

        assertThat(config1).isEqualTo(config2);
    }

    @Test
    void testHashCode() {
        WillConfig config1 = new WillConfig(true, "topic", "msg", 10, 1, true);
        WillConfig config2 = new WillConfig(true, "topic", "msg", 10, 1, true);

        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }

    @Test
    void testToString() {
        WillConfig config = new WillConfig(
            true,
            "topic/test",
            "message content",
            10,
            1,
            true
        );

        String str = config.toString();

        assertThat(str).contains("topic/test");
        assertThat(str).contains("message content");
    }

    @Test
    void testNotEqual() {
        WillConfig config1 = new WillConfig(true, "topic1", "msg", 10, 1, true);
        WillConfig config2 = new WillConfig(true, "topic2", "msg", 10, 1, true);

        assertThat(config1).isNotEqualTo(config2);
    }

    @Test
    void testWithNullValues() {
        WillConfig config = new WillConfig();
        config.setWillFlag(null);
        config.setWillTopic(null);

        assertThat(config.getWillFlag()).isNull();
        assertThat(config.getWillTopic()).isNull();
    }
}
