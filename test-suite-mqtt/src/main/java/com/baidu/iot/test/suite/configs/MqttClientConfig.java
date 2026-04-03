
package com.baidu.iot.test.suite.configs;


import com.baidu.iot.test.suite.WillConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.net.ssl.SSLContext;

/**
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MqttClientConfig {

    private String protocol;
    private String localAddress;
    private String host;
    private int port;
    private int keepAliveInSec = 120;
    private String clientId;
    private boolean isEmptyClientId = false;
    private int ackTimeoutInSec = 120;
    private int reconnectMaxAttempts;
    private int reconnectIntervalInMs = 1000;
    private int connectTimeoutInMs = 30000;
    private int maxInflightQueue = 1000;
    private String tenantId;
    private int thingIdStartAt = 0;
    private String thingIdPrefix;
    private String username;
    private String password;
    private SSLContext sslContext;
    private boolean cleanSession;
    private long expiryIntervalInSec;
    private String authType;

    /**
     * 医嘱消息配置
     */
    private WillConfig willConfig;

    /**
     * 是否在任务发生异常时结束会话（模拟异常断开）,todo 当前未实现
     */
    private boolean exceptionEnds;

}
