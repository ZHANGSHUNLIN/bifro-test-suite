/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.iot.test.suite.models;

import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TopicFilter model.
 */
class TopicFilterTest {

    @Test
    void testTopicFilter_constructor_shouldCreateInstance() {
        // given
        String topicName = "test/topic";
        MqttQoS qos = MqttQoS.AT_LEAST_ONCE;

        // when
        TopicFilter topicFilter = new TopicFilter(topicName, qos);

        // then
        assertThat(topicFilter.getName()).isEqualTo(topicName);
        assertThat(topicFilter.getQos()).isEqualTo(qos);
    }

    @Test
    void testTopicFilter_setters_shouldUpdateFields() {
        // given
        TopicFilter topicFilter = new TopicFilter("old/topic", MqttQoS.AT_MOST_ONCE);

        // when
        topicFilter.setName("new/topic");
        topicFilter.setQos(MqttQoS.EXACTLY_ONCE);

        // then
        assertThat(topicFilter.getName()).isEqualTo("new/topic");
        assertThat(topicFilter.getQos()).isEqualTo(MqttQoS.EXACTLY_ONCE);
    }

    @Test
    void testTopicFilter_withAllQosLevels_shouldWork() {
        // given
        TopicFilter qoS0 = new TopicFilter("topic/qos0", MqttQoS.AT_MOST_ONCE);
        TopicFilter qoS1 = new TopicFilter("topic/qos1", MqttQoS.AT_LEAST_ONCE);
        TopicFilter qoS2 = new TopicFilter("topic/qos2", MqttQoS.EXACTLY_ONCE);

        // then
        assertThat(qoS0.getQos()).isEqualTo(MqttQoS.AT_MOST_ONCE);
        assertThat(qoS1.getQos()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(qoS2.getQos()).isEqualTo(MqttQoS.EXACTLY_ONCE);
    }

    @Test
    void testTopicFilter_withWildcardTopic_shouldWork() {
        // given
        String wildcardTopic = "sensor/+/data";

        // when
        TopicFilter topicFilter = new TopicFilter(wildcardTopic, MqttQoS.AT_LEAST_ONCE);

        // then
        assertThat(topicFilter.getName()).isEqualTo(wildcardTopic);
    }

    @Test
    void testTopicFilter_equality_shouldUseLombok() {
        // given
        TopicFilter filter1 = new TopicFilter("same/topic", MqttQoS.AT_LEAST_ONCE);
        TopicFilter filter2 = new TopicFilter("same/topic", MqttQoS.AT_LEAST_ONCE);
        TopicFilter filter3 = new TopicFilter("different/topic", MqttQoS.AT_LEAST_ONCE);

        // then - @Data generates equals and hashCode
        assertThat(filter1).isEqualTo(filter2);
        assertThat(filter1).isNotEqualTo(filter3);
        assertThat(filter1.hashCode()).isEqualTo(filter2.hashCode());
    }
}
