
package com.baidu.iot.test.suite;

/**
 * Simplified task stages for state machine.
 * <p>
 */
public enum TaskStage {

    INIT,
    ASSIGNED,
    START,
    /**
     * INIT_CLIENT 只有Conn任务使用
     */
    INIT_CLIENT,

    /**
     * INIT_PUB_CLIENT 和 INIT_SUB_CLIENT 只有pubsub任务使用
     */
    INIT_PUB_CLIENT,
    INIT_SUB_CLIENT,

    PUB_SUB_CLIENT_READY,
    PUB_SUB_CLIENT_START,

    PUB_CLIENT_CONN,
    SUB_CLIENT_CONN,



    /**
     * INIT_KAFKA_CLIENT 只有Kafka任务使用
     */
    INIT_KAFKA_CLIENT,
    /**
     * PRODUCING 只有Kafka生产者任务使用
     */
    PRODUCING,
    /**
     * DATABASE_CONNECTING 只有数据库任务使用
     */
    DATABASE_CONNECTING,
    /**
     * DATABASE_OPERATING 只有数据库任务使用
     */
    DATABASE_OPERATING,
    ONGOING,
    SHUTTING,
    SHUTDOWN,
    STOPPED,
    FAILED,
    TIMEOUT,
}
