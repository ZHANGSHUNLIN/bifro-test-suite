
package com.baidu.iot.test.suite;

import io.vertx.core.eventbus.DeliveryOptions;

/**
 * Task events for state machine transitions.
 *
 */
public enum TaskEvent {

    START_TASK,

    INIT_CONN,

    INIT_PUB,
    PUB_READY,

    INIT_SUB,
    SUB_READY,

    PUB_CONN,
    SUB_CONN,


    /**
     * INIT_KAFKA 只有Kafka任务使用
     */
    INIT_KAFKA,
    /**
     * START_KAFKA_TASK 只有Kafka任务使用
     */
    START_KAFKA_TASK,
    START_CONN_CLIENT_TASK,
    START_PUBSUB_CLIENT_TASK,
    COLLECT_RESULTS,
    SHUTTING,
    SHUTDOWN,
    SHUTDOWN_COMPLETE,
    STOP,
    INTERRUPT,
    TIMEOUT,
    FAILURE,
    CLIENT_CONNECTED,
    CLIENT_FAILED,
    ALL_CLIENTS_READY,

    ;

    public static DeliveryOptions getLocalDeliveryOptions() {
        DeliveryOptions deliveryOptions = new DeliveryOptions();
        deliveryOptions.setLocalOnly(true);
        return deliveryOptions;
    }
}
