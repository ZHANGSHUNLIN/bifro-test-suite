package com.baidu.iot.test.suite.kafka.client;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka client wrapper interface.
 */
public interface KafkaClientWrapper {

    /**
     * Connect to Kafka cluster.
     *
     * @return CompletableFuture that completes when connected
     */
    CompletableFuture<Void> connect();

    /**
     * Disconnect from Kafka cluster.
     *
     * @return CompletableFuture that completes when disconnected
     */
    CompletableFuture<Void> disconnect();

    /**
     * Produce messages to Kafka topic.
     *
     * @param message message to produce
     * @return CompletableFuture that completes when message is produced
     */
    CompletableFuture<Void> produce(String message);

    /**
     * Consume messages from Kafka topic.
     *
     * @param messageHandler handler for consumed messages
     * @return CompletableFuture that completes when consumption starts
     */
    CompletableFuture<Void> consume(MessageHandler messageHandler);

    /**
     * Subscribe to Kafka topic.
     *
     * @param topics topics to subscribe
     * @return CompletableFuture that completes when subscribed
     */
    CompletableFuture<Void> subscribe(Set<String> topics);

    /**
     * Unsubscribe from Kafka topic.
     *
     * @return CompletableFuture that completes when unsubscribed
     */
    CompletableFuture<Void> unsubscribe();

    /**
     * Check if connected.
     *
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Get client configuration.
     *
     * @return client configuration
     */
    com.baidu.iot.test.suite.kafka.config.KafkaClientConfig getConfig();

    /**
     * Get client id.
     *
     * @return client id
     */
    String getClientId();

    /**
     * Get consumer metrics.
     *
     * @return metrics map
     */
    Map<String, Object> getMetrics();

    /**
     * Get producer metrics.
     *
     * @return metrics map
     */
    Map<String, Object> getProducerMetrics();

    /**
     * Message handler interface.
     */
    @FunctionalInterface
    interface MessageHandler {

        /**
         * Handle consumed message.
         *
         * @param topic   topic name
         * @param partition partition number
         * @param offset  message offset
         * @param key     message key
         * @param value   message value
         */
        void handle(String topic, int partition, long offset, String key, String value);
    }
}
