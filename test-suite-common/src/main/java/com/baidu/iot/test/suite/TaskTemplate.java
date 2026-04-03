
package com.baidu.iot.test.suite;

import lombok.Getter;

/**
 * Task template enumeration.
 * Each template corresponds to a fixed execution flow.
 */
@Getter
public enum TaskTemplate {

    // ===== Connection Task Templates =====

    /**
     * Standard connection task:
     * Initialize clients -> Connect at rate -> Wait for all connected ->
     * Collect results -> Wait stressDuration -> Disconnect at rate
     */
    CONN_STANDARD("连接 - 标准模式"),

    /**
     * Connection task with immediate disconnect:
     * Initialize clients -> Connect at rate -> Disconnect immediately after all connected
     */
    CONN_IMMEDIATE_DISCONNECT("连接 - 立即断开"),

    // ===== Pub/Sub Task Templates =====

    /**
     * Standard pub/sub-task:
     * Init pub clients -> Init sub clients -> Wait for all ready ->
     * Start pub/sub -> Collect results -> Disconnect
     */
    PUBSUB_STANDARD("发布订阅 - 标准模式"),

    /**
     * Pub/sub task with single message:
     * Init pub clients -> Init sub clients -> Connect ->
     * Publish one message -> Wait -> Disconnect
     */
    PUBSUB_SINGLE_MESSAGE("发布订阅 - 单条消息"),

    /**
     * Pub/sub-task with single subscribe:
     * Init sub clients -> Connect -> Subscribe once -> Wait -> Disconnect
     */
    PUBSUB_SINGLE_SUBSCRIBE("发布订阅 - 单次订阅"),

    // ===== Kafka Task Templates =====

    /**
     * Standard Kafka producer task:
     * Init Kafka clients -> Connect -> Produce messages -> Collect results -> Disconnect
     */
    KAFKA_PRODUCER_STANDARD("Kafka - 生产者标准模式"),

    /**
     * Kafka consumer task:
     * Init Kafka consumers -> Connect -> Consume messages -> Collect results -> Disconnect
     */
    KAFKA_CONSUMER_STANDARD("Kafka - 消费者标准模式"),

    // ===== Database Task Templates =====

    /**
     * MySQL connection test:
     * Connect -> Execute queries -> Collect results -> Disconnect
     */
    MYSQL_STANDARD("MySQL - 标准模式"),

    /**
     * PostgreSQL connection test:
     * Connect -> Execute queries -> Collect results -> Disconnect
     */
    POSTGRESQL_STANDARD("PostgreSQL - 标准模式"),

    /**
     * MongoDB connection test:
     * Connect -> Execute operations -> Collect results -> Disconnect
     */
    MONGODB_STANDARD("MongoDB - 标准模式"),

    // ===== Future Extension =====

    /**
     * Custom template for future extension.
     */
    CUSTOM("自定义模板");

    private final String label;

    TaskTemplate(String label) {
        this.label = label;
    }

}
