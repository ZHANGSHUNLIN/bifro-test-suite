/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.iot.test.suite.worker;

import static com.baidu.iot.test.suite.client.MqttCloudHelper.generateUsername;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.WillConfig;
import com.baidu.iot.test.suite.client.MqttCloudHelper;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.worker.utils.ConfigHelper;
import com.google.common.util.concurrent.RateLimiter;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@Data
@Slf4j
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private TaskStage taskWorkStage = TaskStage.INIT;
    private String taskId;
    @Builder.Default
    private String nodeId = "";
    private TaskType taskType;
    private String protocol;
    private List<String> localAddresses = new ArrayList<>();
    private List<TaskBroker> brokers = new ArrayList<>();
    private String username;
    private String password;
    private String tenantId;
    /**
     * tingid 的起始位置
     */
    @Builder.Default
    private int thingIdStartAt = 0;
    private String thingIdPrefix;
    private boolean cleanSession;
    @Builder.Default
    private int keepAliveInSec = 120;
    @Builder.Default
    private int ackTimeoutInSec = 120;
    @Builder.Default
    private int reconnectMaxAttempts = 10;
    @Builder.Default
    private int reconnectIntervalInMs = 5000;
    @Builder.Default
    private int connectTimeoutInMs = 10000;
    @Builder.Default
    private int maxInflightQueue = 200;
    private int totalClientCount;
    @Builder.Default
    private int fanOut = 1;
    @Builder.Default
    private int fanIn = 1;
    private String topic;
    private MqttQoS qos;
    @Builder.Default
    private boolean fixedTopic = false;
    private boolean isWildcard;
    @Builder.Default
    private int messageSize = 32;
    @Builder.Default
    private int pubIntervalInMs = 10000;
    private int stressDurationInSec;
    @Builder.Default
    private int stageTimeoutInSec = 30;
    @Builder.Default
    private int delayAfterReadyInSec = 1;
    @Builder.Default
    private int skipStatsPeriod = 0;
    @Builder.Default
    private boolean retain = false;
    @Builder.Default
    private boolean isMqtt5 = false;
    private String authType;
    @Builder.Default
    private boolean isEmptyClientId = false;
    @Builder.Default
    private long expiryIntervalInSec = 120;
    @Builder.Default
    private boolean pubOnly = false;
    @Builder.Default
    private boolean subOnly = false;
    @Builder.Default
    private int connectRate = 500;
    @Builder.Default
    private int disconnectRate = 500;
    @Builder.Default
    private boolean enableAutoMultiAddress = false;
    @Builder.Default
    private String group = "";
    private String[] lifecycleActions;
    @Builder.Default
    private Map<String, Object> lifecycleActionsConfig = new HashMap<>();
    @Builder.Default
    private WillConfig willConfig = new WillConfig();
    /**
     * 是否在任务发生异常时结束会话（模拟异常断开）
     */
    @Builder.Default
    private boolean exceptionEnds = false;
    @Builder.Default
    private int tagPeriodIntervalInSec = 30;
    @Builder.Default
    private int localAddrIndex = 0;

    public synchronized RateLimiter getConnectRateLimiter() {
        return RateLimiter.create(connectRate);
    }

    public synchronized RateLimiter getDisConnectRateLimiter() {
        return RateLimiter.create(disconnectRate);
    }

    public synchronized List<String> loadLocalAddresses() {
        if (this.localAddresses == null || this.localAddresses.isEmpty()) {
            try {
                for (Enumeration<NetworkInterface> iFaces = NetworkInterface.getNetworkInterfaces();
                     iFaces.hasMoreElements(); ) {
                    NetworkInterface iFace = iFaces.nextElement();
                    log.debug(iFace.isUp() ? "up" : "down");
                    if (iFace.isUp()) {
                        for (Enumeration<InetAddress> inetAddresses = iFace.getInetAddresses();
                             inetAddresses.hasMoreElements(); ) {
                            InetAddress inetAddr = inetAddresses.nextElement();
                            if (inetAddr != null && !inetAddr.isLoopbackAddress()) {
                                if (inetAddr.isSiteLocalAddress()) {
                                    log.info("本地网卡IP<UNK>{}", inetAddr.getHostAddress());
                                    localAddresses.add(inetAddr.getHostAddress());
                                }
                            }
                        }
                    }
                }
            } catch (SocketException e) {
                log.error("Failed to discover local address.", e);
            }
        }
        return this.localAddresses;
    }

    public String nextLocalAddress() {
        if (!this.enableAutoMultiAddress) {
            return null;
        }
        if (localAddresses.isEmpty()) {
            return loadLocalAddresses().get(localAddrIndex++ % localAddresses.size());
        }
        return localAddresses.get(localAddrIndex++ % localAddresses.size());
    }

    public MqttClientConfig getMqttClientConfig(int clientIndex, AtomicInteger subscribeCount) {
        MqttClientConfig mqttClientConfig = new MqttClientConfig();
        TaskBroker taskBroker = brokers.get(ThreadLocalRandom.current().nextInt(brokers.size()));
        mqttClientConfig.setHost(taskBroker.getHost());
        mqttClientConfig.setPort(taskBroker.getPort());
        String clientId = String.format("Conn_%s_%d", UUID.randomUUID(), clientIndex);
        mqttClientConfig.setClientId(clientId);

        String thingId = "DevOnly";

        if (Objects.equals(authType, "normal")) {
            ConfigHelper.fillCommonMqttConfig(mqttClientConfig, this);
        } else if (Objects.equals(authType, "byoc")) {
            String thingIdPrefix = this.thingIdPrefix == null
                    ? "demo_" : this.thingIdPrefix;
            thingId = thingIdPrefix + subscribeCount.getAndIncrement();
            MqttCloudHelper.UsernamePassword
                    usernamePassword =
                    generateUsername(tenantId, thingId, password, clientId);
            ConfigHelper.fillCommonMqttConfig(mqttClientConfig, this);
            mqttClientConfig.setUsername(usernamePassword.getUsername());
            mqttClientConfig.setPassword(usernamePassword.getPassword());
            mqttClientConfig.setTenantId(tenantId);
        } else if (Objects.equals(authType, "iotCore")) {
            // todo
            throw new RuntimeException("todo implement.");
        }
        WillConfig newWillConfig = new WillConfig();
        String willTopic = willConfig.getWillTopic();
        // 医嘱消息topic占位符处理
        if (willConfig.getWillFlag()) {
            newWillConfig.setWillFlag(true);
            newWillConfig.setWillMessage(willConfig.getWillMessage());
            newWillConfig.setWillQos(willConfig.getWillQos());
            newWillConfig.setWillRetain(willConfig.getWillRetain());
            newWillConfig.setWillMessageLen(willConfig.getWillMessageLen());
            if (StringUtils.isNotBlank(willTopic)) {
                willTopic = willTopic.replace("{thingId}", thingId)
                        .replace("{clientId}", clientId.substring(Math.max(0, clientId.length() - 8)))
                        .replace("{tenantId}", tenantId == null ? "" : tenantId);
                newWillConfig.setWillTopic(willTopic);
                log.trace("Generated will topic: {}", willTopic);
            }
        }

        mqttClientConfig.setWillConfig(newWillConfig);
        mqttClientConfig.setExceptionEnds(exceptionEnds);
        return mqttClientConfig;
    }

    public static TaskConfig newInstance(TaskConfig origin) {
        TaskConfig newConfig = new TaskConfig();
        try {
            BeanUtils.copyProperties(newConfig, origin);
        } catch (Exception ignored) {
        }
        newConfig.taskId = RandomStringUtils.random(8, true, true) + "_" + System.currentTimeMillis();
        return newConfig;
    }

    public enum TaskType {
        CONN,
        PUBSUB
    }
}
