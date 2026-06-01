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

package org.apache.bifromq.testsuite;

import io.micrometer.core.instrument.Tags;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.payload.PayloadStrategy;
import org.apache.bifromq.testsuite.payload.PayloadStrategyFactory;
import org.apache.bifromq.testsuite.qps.DataDrivenQpsStrategy;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.ratelimit.TokenBucketRateLimiter;
import org.apache.bifromq.testsuite.topic.TopicSelector;
import org.apache.bifromq.testsuite.topic.TopicSelectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class PubMqttClientTask extends MqttClientTask {

    private static final Logger PUB_LOGGER = LoggerFactory.getLogger("pubLogger");
    private static final long QPS_UPDATE_INTERVAL_MS = 100;

    private final AtomicLong pubCount = new AtomicLong(0);
    private final PayloadStrategy payloadStrategy;
    private final QpsStrategy qpsStrategy;
    private final TokenBucketRateLimiter rateLimiter;
    private final TopicSelector topicSelector;

    private Long qpsUpdateTimerId;

    private long startTimeMs;

    @Builder
    public PubMqttClientTask(@NonNull Vertx vertx,
                             @NonNull ClientTaskConfig taskConfig,
                             @NonNull MqttClientConfig mqttClientConfig,
                             AtomicReference<TaskStage> taskStage) {
        super(vertx, taskConfig, mqttClientConfig, taskStage);
        this.payloadStrategy = PayloadStrategyFactory.create(
            taskConfig.getPayloadMode(), taskConfig.getPayloadTemplate(),
            mqttClientConfig.getClientId(), taskConfig.getTaskId());
        this.qpsStrategy = buildQpsStrategy(taskConfig);
        this.topicSelector = TopicSelectors.roundRobin(resolvePublishTopics(taskConfig));

        if (taskConfig.getProfileQpsSpec() != null || taskConfig.getWaveQpsSpec() != null) {
            int initialQps = qpsStrategy.currentQps(0);
            this.rateLimiter = new TokenBucketRateLimiter(initialQps);
        } else {
            this.rateLimiter = new TokenBucketRateLimiter(taskConfig.getPublishRate());
        }
    }

    private static QpsStrategy buildQpsStrategy(ClientTaskConfig config) {

        if (config.getProfileQpsSpec() != null) {
            return new DataDrivenQpsStrategy(config.getProfileQpsSpec());
        }
        return QpsStrategy.fromWaveSpec(config.getWaveQpsSpec(), 1);
    }

    private static List<String> resolvePublishTopics(ClientTaskConfig taskConfig) {
        List<String> topics = taskConfig.getPubTopics();
        if (topics != null && !topics.isEmpty()) {
            return topics;
        }
        return List.of(taskConfig.getPubTopic());
    }

    private long initialPublishJitterMs() {
        double publishRate = qpsStrategy.isDynamic()
            ? Math.max(1D, qpsStrategy.currentQps(0))
            : taskConfig.getPublishRate();
        return initialPublishJitterMs(publishRate);
    }

    private static long initialPublishJitterMs(double publishRate) {
        long intervalMs = Math.max(1L, Math.round(1000D / publishRate));
        if (intervalMs <= 1) {
            return 1L;
        }
        return ThreadLocalRandom.current().nextLong(1L, intervalMs + 1L);
    }

    @Override
    public CompletableFuture<Void> connect() {
        return connectWrapper(connectionStatus -> wrapClientContext("StartConnClients", () -> {
        }).run());
    }

    public void startPublishing() {
        startTimeMs = System.currentTimeMillis();

        long jitterMs = initialPublishJitterMs();
        this.eventLoop.execute(wrapClientContext("StartPubSubClients", () ->
            vertx.setTimer(jitterMs, e -> {
                wrapClientContext("StartPubSubClients", () -> {
                    rateLimiter.startContinuous(index -> {
                        try (var ignored = enterClientContext("StartPubSubClients")) {
                            publish(index);
                        }
                        return CompletableFuture.completedFuture(null);
                    });

                    if (qpsStrategy.isDynamic()) {
                        qpsUpdateTimerId = vertx.setPeriodic(QPS_UPDATE_INTERVAL_MS, tick ->
                            wrapClientContext("StartPubSubClients", () -> {
                                long elapsed = System.currentTimeMillis() - startTimeMs;
                                int targetQps = qpsStrategy.currentQps(elapsed);
                                rateLimiter.setRate(targetQps);
                            }).run());
                    }
                }).run();
            })));
    }

    public void stopPublishing() {
        rateLimiter.dispose();
        if (qpsUpdateTimerId != null) {
            vertx.cancelTimer(qpsUpdateTimerId);
            qpsUpdateTimerId = null;
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        stopPublishing();
        return super.close();
    }

    @Override
    public OptionalLong getMessageCount() {
        return OptionalLong.of(pubCount.get());
    }

    public CompletableFuture<Void> publishOnce(long index) {

        TaskStage currentStage = taskStage.get();
        if (currentStage != TaskStage.ONGOING && currentStage != TaskStage.STARTING) {
            log.debug("Skipping publish: taskStage={}, clientId={}", currentStage, clientConfig.getClientId());
            return CompletableFuture.completedFuture(null);
        }

        long startNano = System.nanoTime();
        byte[] payload = payloadStrategy.buildPayload(index, taskConfig.getMessageSize());

        String pubTopic = topicSelector.nextTopic();
        if (taskConfig.isRandomPublishing()) {
            pubTopic = pubTopic + "/" + System.currentTimeMillis();
        }

        String taskId = taskConfig.getTaskId();
        String nodeId = taskConfig.getNodeId();
        String clientId = clientConfig.getClientId();
        int qos = taskConfig.getMessageQos().value();
        Tags tags = Tags.of("taskId", taskId, "nodeId", nodeId);
        Tags qosTags = Tags.of("taskId", taskId, "nodeId", nodeId, "qos", String.valueOf(qos));

        PUB_LOGGER.debug("publish payload: {}, topic: {}, qos: {}, isDup: {}, isRetain: {}",
            payload.length, pubTopic, qos, false, taskConfig.isRetain());
        return mqttClientWrapper.publish(payload, pubTopic, qos, false, taskConfig.isRetain())
            .whenComplete((v, e) -> {
                if (e != null) {
                    log.warn("Publish message failed, taskId={}, clientId={}, reason={}",
                        taskId, clientId, e.getMessage(), e);
                    MetricsHelper.counter(BifroTaskMetric.PUBLISH_FAILURE_COUNT, tags);
                } else {
                    MetricsHelper.counter(BifroTaskMetric.PUBLISH_COMPLETION_COUNT, tags);
                    MetricsHelper.counter(qosMetric(qos), qosTags);
                    MetricsHelper.counter(BifroTaskMetric.THROUGHPUT_MESSAGES, tags);
                    MetricsHelper.counter(BifroTaskMetric.THROUGHPUT_BYTES, payload.length,
                        "taskId", taskId, "nodeId", nodeId);
                    pubCount.incrementAndGet();
                    if (payloadStrategy.supportsLatencyTracking()) {
                        long latencyNanos = System.nanoTime() - startNano;
                        MetricsHelper.recordTimeNanos(BifroTaskMetric.PUBLISH_LATENCY, latencyNanos,
                            "taskId", taskId, "nodeId", nodeId);
                    }
                }
            });
    }

    private BifroTaskMetric qosMetric(int qos) {
        return switch (qos) {
            case 1 -> BifroTaskMetric.QOS1_MESSAGE_COUNT;
            case 2 -> BifroTaskMetric.QOS2_MESSAGE_COUNT;
            default -> BifroTaskMetric.QOS0_MESSAGE_COUNT;
        };
    }

    private void publish(long index) {
        publishOnce(index);
    }
}
