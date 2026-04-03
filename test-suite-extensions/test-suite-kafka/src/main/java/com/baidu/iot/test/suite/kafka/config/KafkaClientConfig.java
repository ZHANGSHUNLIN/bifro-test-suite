package com.baidu.iot.test.suite.kafka.config;

import lombok.Data;

/**
 * Configuration for Kafka client.
 */
@Data
public class KafkaClientConfig {

    /**
     * Kafka bootstrap servers.
     */
    private String bootstrapServers;

    /**
     * Client id for identification.
     */
    private String clientId;

    /**
     * Topic name.
     */
    private String topic;

    /**
     * Consumer group id (for consumer only).
     */
    private String consumerGroupId;

    /**
     * Auto offset reset policy.
     */
    private String autoOffsetReset;

    /**
     * Enable auto commit.
     */
    private Boolean enableAutoCommit;

    /**
     * Max poll records.
     */
    private Integer maxPollRecords;

    /**
     * Request timeout in milliseconds.
     */
    private Integer requestTimeoutMs;

    /**
     * Session timeout in milliseconds.
     */
    private Integer sessionTimeoutMs;

    /**
     * Maximum retry attempts.
     */
    private Integer reconnectMaxAttempts;

    /**
     * Reconnect interval in milliseconds.
     */
    private Integer reconnectIntervalInMs;

    /**
     * Create builder.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for KafkaClientConfig.
     */
    public static class Builder {

        private String bootstrapServers;
        private String clientId;
        private String topic;
        private String consumerGroupId;
        private String autoOffsetReset = "earliest";
        private Boolean enableAutoCommit = false;
        private Integer maxPollRecords = 100;
        private Integer requestTimeoutMs = 30000;
        private Integer sessionTimeoutMs = 30000;
        private Integer reconnectMaxAttempts = 3;
        private Integer reconnectIntervalInMs = 1000;

        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder consumerGroupId(String consumerGroupId) {
            this.consumerGroupId = consumerGroupId;
            return this;
        }

        public Builder autoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
            return this;
        }

        public Builder enableAutoCommit(boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
            return this;
        }

        public Builder maxPollRecords(int maxPollRecords) {
            this.maxPollRecords = maxPollRecords;
            return this;
        }

        public Builder requestTimeoutMs(int requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
            return this;
        }

        public Builder sessionTimeoutMs(int sessionTimeoutMs) {
            this.sessionTimeoutMs = sessionTimeoutMs;
            return this;
        }

        public Builder reconnectMaxAttempts(int reconnectMaxAttempts) {
            this.reconnectMaxAttempts = reconnectMaxAttempts;
            return this;
        }

        public Builder reconnectIntervalInMs(int reconnectIntervalInMs) {
            this.reconnectIntervalInMs = reconnectIntervalInMs;
            return this;
        }

        public KafkaClientConfig build() {
            KafkaClientConfig config = new KafkaClientConfig();
            config.setBootstrapServers(bootstrapServers);
            config.setClientId(clientId);
            config.setTopic(topic);
            config.setConsumerGroupId(consumerGroupId);
            config.setAutoOffsetReset(autoOffsetReset);
            config.setEnableAutoCommit(enableAutoCommit);
            config.setMaxPollRecords(maxPollRecords);
            config.setRequestTimeoutMs(requestTimeoutMs);
            config.setSessionTimeoutMs(sessionTimeoutMs);
            config.setReconnectMaxAttempts(reconnectMaxAttempts);
            config.setReconnectIntervalInMs(reconnectIntervalInMs);
            return config;
        }
    }
}
