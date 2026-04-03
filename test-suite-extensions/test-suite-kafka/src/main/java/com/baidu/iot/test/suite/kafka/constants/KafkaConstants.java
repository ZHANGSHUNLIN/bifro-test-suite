package com.baidu.iot.test.suite.kafka.constants;

/**
 * Constants for Kafka client.
 */
public final class KafkaConstants {

    private KafkaConstants() {
    }

    /**
     * Default bootstrap servers.
     */
    public static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";

    /**
     * Default consumer group id.
     */
    public static final String DEFAULT_CONSUMER_GROUP_ID = "test-suite-consumer-group";

    /**
     * Default auto offset reset.
     */
    public static final String DEFAULT_AUTO_OFFSET_RESET = "earliest";

    /**
     * Default enable auto commit.
     */
    public static final boolean DEFAULT_ENABLE_AUTO_COMMIT = false;

    /**
     * Default max poll records.
     */
    public static final int DEFAULT_MAX_POLL_RECORDS = 100;

    /**
     * Default request timeout in ms.
     */
    public static final int DEFAULT_REQUEST_TIMEOUT_MS = 30000;

    /**
     * Default session timeout in ms.
     */
    public static final int DEFAULT_SESSION_TIMEOUT_MS = 30000;
}
