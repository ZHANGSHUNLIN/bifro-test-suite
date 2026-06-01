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

package org.apache.bifromq.testsuite.metric;

import lombok.Getter;

public enum BifroTaskMetric implements TaskMetric {
    ILLEGAL_TASK_STATE("bifro_task_metric_illegal_task_state"),
    ILLEGAL_STATE_CLIENT_CLOSED("bifro_task_metric_illegal_client_closed_state"),
    RECONNECT_COUNT("bifro_task_metric_reconnect_count"),
    RECONNECT_LIMIT_EXCEEDED("bifro_task_metric_reconnect_limit_exceeded"),
    CONNECT_EXCEPTION_COUNT("bifro_task_metric_connect_exception_count"),
    LOCAL_PORT_BIND_FAILURE_COUNT("bifro_task_metric_local_port_bind_failure_count"),
    SUBSCRIBE_COMPLETION_COUNT("bifro_task_metric_subscribe_completion_count"),
    PUBLISH_COMPLETION_COUNT("bifro_task_metric_publish_completion_count"),
    PUBLISH_COUNT("bifro_task_metric_publish_count"),
    PUBLISH_COMPLETION_EXPIRATION_COUNT("bifro_task_metric_publish_completion_expiration_count"),
    PUBLISH_COMPLETION_UNKNOWN_PACKET_ID_COUNT("bifro_task_metric_publish_completion_unknown_packet_id_count"),
    CONNECT_ATTEMPT_COUNT("bifro_task_metric_connect_attempt_count"),
    CONNECT_SUCCESS_COUNT("bifro_task_metric_connect_success_count"),
    SUBSCRIBE_COMPLETION_EXPIRATION_COUNT("bifro_task_metric_subscribe_completion_expiration_count"),
    DISCONNECT_COMPLETION_COUNT("bifro_task_metric_disconnect_completion_count"),
    DISCONNECT_COMPLETION_EXPIRATION_COUNT("bifro_task_metric_disconnect_completion_expiration_count"),
    UNSUBSCRIBE_COMPLETION_EXPIRATION_COUNT("bifro_task_metric_unsubscribe_completion_expiration_count"),
    UNSUBSCRIBE_COMPLETION_COUNT("bifro_task_metric_unsubscribe_completion_count"),
    CLIENT_CLOSE_COUNT("bifro_task_metric_client_close_count"),
    CLIENT_CLOSE_EXCEPTION_COUNT("bifro_task_metric_client_close_exception_count"),

    
    TASK_START_COUNT("bifro_task_metric_task_start_count"),
    TASK_COMPLETE_COUNT("bifro_task_metric_task_complete_count"),
    TASK_FAILURE_COUNT("bifro_task_metric_task_failure_count"),
    TASK_STOP_COUNT("bifro_task_metric_task_stop_count"),
    TASK_TIMEOUT_COUNT("bifro_task_metric_task_timeout_count"),

    

    
    CLIENT_CREATED_COUNT("bifro_task_metric_client_created_count"),
    CLIENT_FAILURE_COUNT("bifro_task_metric_client_failure_count"),
    CLIENT_PLANNED_GAUGE("bifro_task_metric_client_planned_gauge"),
    CLIENT_READY_GAUGE("bifro_task_metric_client_ready_gauge"),
    CLIENT_ACTIVE_GAUGE("bifro_task_metric_client_active_gauge"),

    
    MESSAGE_RECEIVED_COUNT("bifro_task_metric_message_received_count"),
    MESSAGE_DUPLICATE_COUNT("bifro_task_metric_message_duplicate_count"),
    SUBSCRIBE_FAILURE_COUNT("bifro_task_metric_subscribe_failure_count"),
    PUBLISH_FAILURE_COUNT("bifro_task_metric_publish_failure_count"),

    
    QOS0_MESSAGE_COUNT("bifro_task_metric_qos0_message_count"),
    QOS1_MESSAGE_COUNT("bifro_task_metric_qos1_message_count"),
    QOS2_MESSAGE_COUNT("bifro_task_metric_qos2_message_count"),

    
    THROUGHPUT_MESSAGES("bifro_task_metric_throughput_messages"),
    THROUGHPUT_BYTES("bifro_task_metric_throughput_bytes"),

    
    TASK_DURATION("bifro_task_metric_task_duration"),

    
    CONNECT_LATENCY("bifro_task_metric_connect_latency"),
    PUBLISH_LATENCY("bifro_task_metric_publish_latency"),
    SUBSCRIBE_LATENCY("bifro_task_metric_subscribe_latency"),
    MESSAGE_DELIVERY_LATENCY("bifro_task_metric_message_delivery_latency"),

    
    PUBACK_LATENCY("bifro_task_metric_puback_latency"),    

    
    
    UNEXPECTED_DISCONNECT_COUNT("bifro_task_metric_unexpected_disconnect_count"),

    
    CHAOS_BEHAVIOR_COUNT("bifro_chaos_behavior_total");

    @Getter
    private final String name;

    BifroTaskMetric(String name) {
        this.name = name;
    }

}
