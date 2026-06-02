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

package org.apache.bifromq.testsuite.models;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

class TopicFilterTest {

    @Test
    void testTopicFilter_constructor_shouldCreateInstance() {
        
        String topicName = "test/topic";
        MqttQoS qos = MqttQoS.AT_LEAST_ONCE;

        
        TopicFilter topicFilter = new TopicFilter(topicName, qos);

        
        assertThat(topicFilter.getName()).isEqualTo(topicName);
        assertThat(topicFilter.getQos()).isEqualTo(qos);
    }

    @Test
    void testTopicFilter_setters_shouldUpdateFields() {
        
        TopicFilter topicFilter = new TopicFilter("old/topic", MqttQoS.AT_MOST_ONCE);

        
        topicFilter.setName("new/topic");
        topicFilter.setQos(MqttQoS.EXACTLY_ONCE);

        
        assertThat(topicFilter.getName()).isEqualTo("new/topic");
        assertThat(topicFilter.getQos()).isEqualTo(MqttQoS.EXACTLY_ONCE);
    }

    @Test
    void testTopicFilter_withAllQosLevels_shouldWork() {
        
        TopicFilter qoS0 = new TopicFilter("topic/qos0", MqttQoS.AT_MOST_ONCE);
        TopicFilter qoS1 = new TopicFilter("topic/qos1", MqttQoS.AT_LEAST_ONCE);
        TopicFilter qoS2 = new TopicFilter("topic/qos2", MqttQoS.EXACTLY_ONCE);

        
        assertThat(qoS0.getQos()).isEqualTo(MqttQoS.AT_MOST_ONCE);
        assertThat(qoS1.getQos()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(qoS2.getQos()).isEqualTo(MqttQoS.EXACTLY_ONCE);
    }

    @Test
    void testTopicFilter_withWildcardTopic_shouldWork() {
        
        String wildcardTopic = "sensor/+/data";

        
        TopicFilter topicFilter = new TopicFilter(wildcardTopic, MqttQoS.AT_LEAST_ONCE);

        
        assertThat(topicFilter.getName()).isEqualTo(wildcardTopic);
    }

    @Test
    void testTopicFilter_equality_shouldUseLombok() {
        
        TopicFilter filter1 = new TopicFilter("same/topic", MqttQoS.AT_LEAST_ONCE);
        TopicFilter filter2 = new TopicFilter("same/topic", MqttQoS.AT_LEAST_ONCE);
        TopicFilter filter3 = new TopicFilter("different/topic", MqttQoS.AT_LEAST_ONCE);

        
        assertThat(filter1).isEqualTo(filter2);
        assertThat(filter1).isNotEqualTo(filter3);
        assertThat(filter1.hashCode()).isEqualTo(filter2.hashCode());
    }
}
