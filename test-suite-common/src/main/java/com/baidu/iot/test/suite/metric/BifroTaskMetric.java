package com.baidu.iot.test.suite.metric;

import lombok.Getter;

public enum BifroTaskMetric implements TaskMetric{
    ILLEGAL_TASK_STATE("bifro_task_metric_illegal_task_state"),
    ILLEGAL_STATE_CLIENT_CLOSED("bifro_task_metric_illegal_client_closed_state"),
    RECONNECT_COUNT("bifro_task_metric_reconnect_count"),
    RECONNECT_LIMIT_EXCEEDED("bifro_task_metric_reconnect_limit_exceeded"),
    CONNECT_EXCEPTION_COUNT("bifro_task_metric_connect_exception_count"),
    SUBSCRIBE_COMPLETION_COUNT("bifro_task_metric_subscribe_completion_count"),
    PUBLISH_COMPLETION_COUNT("bifro_task_metric_publish_completion_count"),
    PUBLISH_COUNT("bifro_task_metric_publish_count"),
    PUBLISH_COMPLETION_EXPIRATION_COUNT("bifro_task_metric_publish_completion_expiration_count"),
    PUBLISH_COMPLETION_UNKNOWN_PACKET_ID_COUNT("bifro_task_metric_publish_completion_unknown_packet_id_count"),
    CONNECT_SUCCESS_COUNT("bifro_task_metric_connect_success_count"),
    SUBSCRIBE_COMPLETION_EXPIRATION_COUNT("bifro_task_metric_subscribe_completion_expiration_count"),
    DISCONNECT_COMPLETION_COUNT("bifro_task_metric_disconnect_completion_count"),
    DISCONNECT_COMPLETION_EXPIRATION_COUNT("bifro_task_metric_disconnect_completion_expiration_count"),
    UNSUBSCRIBE_COMPLETION_EXPIRATION_COUNT("bifro_task_metric_unsubscribe_completion_expiration_count"),
    UNSUBSCRIBE_COMPLETION_COUNT("bifro_task_metric_unsubscribe_completion_count"),
    CLIENT_CLOSE_COUNT("bifro_task_metric_client_close_count"),
    CLIENT_CLOSE_EXCEPTION_COUNT("bifro_task_metric_client_close_exception_count"),
    ;

    BifroTaskMetric(String name) {
        this.name = name;
    }

    @Getter
    private final String name;

}
