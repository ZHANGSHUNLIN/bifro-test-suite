package com.baidu.iot.test.suite.kafka.client;

import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.kafka.config.KafkaClientConfig;
import com.baidu.iot.test.suite.kafka.constants.KafkaConstants;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base Kafka client wrapper.
 */
@Slf4j
public class BaseKafkaClientWrapper implements KafkaClientWrapper {

    protected final Vertx vertx;
    protected final KafkaClientConfig config;
    protected final AtomicBoolean connected = new AtomicBoolean(false);
    protected final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    protected final AtomicReference<TaskStage> taskStage;
    protected KafkaProducer<String, String> producer;
    protected KafkaConsumer<String, String> consumer;
    protected final Map<String, Object> metrics = new ConcurrentHashMap<>();

    public BaseKafkaClientWrapper(Vertx vertx, KafkaClientConfig config, AtomicReference<TaskStage> taskStage) {
        this.vertx = vertx;
        this.config = config;
        this.taskStage = taskStage;
    }

    @Override
    public CompletableFuture<Void> connect() {
        CompletableFuture<Void> connectFuture = new CompletableFuture<>();

        try {
            log.info("Connecting to Kafka: {}, clientId={}", config.getBootstrapServers(), config.getClientId());

            // Create producer
            producer = new KafkaProducer<>(createProducerConfig());
            log.info("Kafka producer created, clientId={}", config.getClientId());

            // Create consumer (if consumer group id is provided)
            if (config.getConsumerGroupId() != null) {
                consumer = new KafkaConsumer<>(createConsumerConfig());
                log.info("Kafka consumer created, clientId={}, groupId={}",
                        config.getClientId(), config.getConsumerGroupId());
            }

            connected.set(true);
            reconnectAttempts.set(0);
            connectFuture.complete(null);

        } catch (Exception e) {
            log.error("Failed to connect to Kafka, clientId={}", config.getClientId(), e);
            handleConnectionFailure(connectFuture, e);
        }

        return connectFuture;
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        CompletableFuture<Void> disconnectFuture = new CompletableFuture<>();

        try {
            log.info("Disconnecting from Kafka, clientId={}", config.getClientId());

            if (consumer != null) {
                consumer.close();
                consumer = null;
                log.info("Kafka consumer closed, clientId={}", config.getClientId());
            }

            if (producer != null) {
                producer.close();
                producer = null;
                log.info("Kafka producer closed, clientId={}", config.getClientId());
            }

            connected.set(false);
            disconnectFuture.complete(null);

        } catch (Exception e) {
            log.error("Failed to disconnect from Kafka, clientId={}", config.getClientId(), e);
            disconnectFuture.completeExceptionally(e);
        }

        return disconnectFuture;
    }

    @Override
    public CompletableFuture<Void> produce(String message) {
        CompletableFuture<Void> produceFuture = new CompletableFuture<>();

        if (!connected.get()) {
            produceFuture.completeExceptionally(new IllegalStateException("Kafka client not connected"));
            return produceFuture;
        }

        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    config.getTopic(),
                    null,
                    message
            );

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Failed to produce message to topic {}, clientId={}",
                            config.getTopic(), config.getClientId(), exception);
                    produceFuture.completeExceptionally(exception);
                } else {
                    log.debug("Message produced to topic {}, partition={}, offset={}, clientId={}",
                            config.getTopic(), metadata.partition(), metadata.offset(), config.getClientId());
                    produceFuture.complete(null);
                }
            });

        } catch (Exception e) {
            log.error("Failed to send message, clientId={}", config.getClientId(), e);
            produceFuture.completeExceptionally(e);
        }

        return produceFuture;
    }

    @Override
    public CompletableFuture<Void> consume(MessageHandler messageHandler) {
        CompletableFuture<Void> consumeFuture = new CompletableFuture<>();

        if (!connected.get() || consumer == null) {
            consumeFuture.completeExceptionally(new IllegalStateException("Kafka consumer not connected"));
            return consumeFuture;
        }

        try {
            log.info("Starting to consume from topic {}, clientId={}", config.getTopic(), config.getClientId());
            consumer.subscribe(Collections.singleton(config.getTopic()));
            consumeFuture.complete(null);
        } catch (Exception e) {
            log.error("Failed to start consuming, clientId={}", config.getClientId(), e);
            consumeFuture.completeExceptionally(e);
        }

        return consumeFuture;
    }

    @Override
    public CompletableFuture<Void> subscribe(Set<String> topics) {
        CompletableFuture<Void> subscribeFuture = new CompletableFuture<>();

        if (!connected.get() || consumer == null) {
            subscribeFuture.completeExceptionally(new IllegalStateException("Kafka consumer not connected"));
            return subscribeFuture;
        }

        try {
            consumer.subscribe(topics);
            log.info("Subscribed to topics {}, clientId={}", topics, config.getClientId());
            subscribeFuture.complete(null);
        } catch (Exception e) {
            log.error("Failed to subscribe to topics, clientId={}", config.getClientId(), e);
            subscribeFuture.completeExceptionally(e);
        }

        return subscribeFuture;
    }

    @Override
    public CompletableFuture<Void> unsubscribe() {
        CompletableFuture<Void> unsubscribeFuture = new CompletableFuture<>();

        if (consumer == null) {
            unsubscribeFuture.complete(null);
            return unsubscribeFuture;
        }

        try {
            consumer.unsubscribe();
            log.info("Unsubscribed from all topics, clientId={}", config.getClientId());
            unsubscribeFuture.complete(null);
        } catch (Exception e) {
            log.error("Failed to unsubscribe, clientId={}", config.getClientId(), e);
            unsubscribeFuture.completeExceptionally(e);
        }

        return unsubscribeFuture;
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public KafkaClientConfig getConfig() {
        return config;
    }

    @Override
    public String getClientId() {
        return config.getClientId();
    }

    @Override
    public Map<String, Object> getMetrics() {
        return Map.copyOf(metrics);
    }

    @Override
    public Map<String, Object> getProducerMetrics() {
        if (producer != null) {
            Map<MetricName, ? extends Metric> producerMetrics = producer.metrics();
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<MetricName, ? extends Metric> entry : producerMetrics.entrySet()) {
                MetricName name = entry.getKey();
                Metric metric = entry.getValue();
                result.put(name.name(), metric.metricValue());
            }
            return result;
        }
        return Collections.emptyMap();
    }

    /**
     * Create producer configuration.
     */
    protected Properties createProducerConfig() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(ProducerConfig.CLIENT_ID_CONFIG, config.getClientId());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                config.getRequestTimeoutMs() != null ? config.getRequestTimeoutMs() : KafkaConstants.DEFAULT_REQUEST_TIMEOUT_MS);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return props;
    }

    /**
     * Create consumer configuration.
     */
    protected Properties createConsumerConfig() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, config.getConsumerGroupId());
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, config.getClientId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                config.getAutoOffsetReset() != null ? config.getAutoOffsetReset() : KafkaConstants.DEFAULT_AUTO_OFFSET_RESET);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                config.getEnableAutoCommit() != null ? config.getEnableAutoCommit() : KafkaConstants.DEFAULT_ENABLE_AUTO_COMMIT);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                config.getMaxPollRecords() != null ? config.getMaxPollRecords() : KafkaConstants.DEFAULT_MAX_POLL_RECORDS);
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                config.getRequestTimeoutMs() != null ? config.getRequestTimeoutMs() : KafkaConstants.DEFAULT_REQUEST_TIMEOUT_MS);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,
                config.getSessionTimeoutMs() != null ? config.getSessionTimeoutMs() : KafkaConstants.DEFAULT_SESSION_TIMEOUT_MS);
        return props;
    }

    /**
     * Handle connection failure.
     */
    private void handleConnectionFailure(CompletableFuture<Void> future, Exception e) {
        connected.set(false);
        int attempts = reconnectAttempts.incrementAndGet();

        if (attempts <= config.getReconnectMaxAttempts()) {
            log.warn("Connection failed, will retry in {}ms (attempt {}/{})",
                    config.getReconnectIntervalInMs(), attempts, config.getReconnectMaxAttempts());

            long timerId = vertx.setTimer(config.getReconnectIntervalInMs(), timer -> {
                log.info("Retrying connection to Kafka, attempt {}/{}", attempts, config.getReconnectMaxAttempts());
                connect().thenAccept(v -> future.complete(null))
                        .exceptionally(ex -> {
                            handleConnectionFailure(future, (Exception) ex);
                            return null;
                        });
            });

        } else {
            log.error("Max reconnection attempts reached ({}), clientId={}",
                    config.getReconnectMaxAttempts(), config.getClientId());
            future.completeExceptionally(e);
        }
    }

    /**
     * Get Kafka producer.
     *
     * @return producer instance
     */
    protected KafkaProducer<String, String> getProducer() {
        return producer;
    }

    /**
     * Get Kafka consumer.
     *
     * @return consumer instance
     */
    protected KafkaConsumer<String, String> getConsumer() {
        return consumer;
    }
}
