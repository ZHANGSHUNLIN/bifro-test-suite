package com.baidu.duhome.bean.dto;

import com.baidu.iot.test.suite.WillConfig;
import com.baidu.iot.test.suite.worker.TaskBroker;
import com.baidu.iot.test.suite.worker.TaskConfig;
import com.baidu.iot.test.suite.worker.TaskConfig.TaskType;
import io.netty.handler.codec.mqtt.MqttQoS;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务请求对象，用于接收外部传入的配置参数
 */
@Data
public class TaskRequest {

    private String taskName;

    /**
     * 任务类型
     */
    @NotNull(message = "任务类型不能为空")
    private TaskType taskType;

    /**
     * 协议类型
     */
    @NotBlank(message = "协议类型不能为空")
    private String protocol = "tcp";

    /**
     * 是否自动获取多网卡地址
     */
    private boolean autoMultiAddress = false;

    /**
     * 本地地址列表
     */
    private List<String> localAddresses = new ArrayList<>();

    /**
     * 服务器主机列表
     */
    @NotEmpty(message = "服务器主机列表不能为空")
    private List<BrokerEntry> brokers = new ArrayList<>();

    /**
     * 服务器端口
     */
    @Min(value = 1, message = "端口号必须大于0")
    @Max(value = 65535, message = "端口号不能超过65535")
    private int port = 1883;


    /**
     * 用户名
     */
    private String username = "";

    /**
     * 密码
     */
    private String password = "";

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * ThingID起始位置
     */
    @Min(value = 0, message = "ThingID起始位置不能小于0")
    private int thingIdStartAt = 0;

    /**
     * ThingID前缀
     */
    private String thingIdPrefix;

    /**
     * 是否清除会话
     */
    private boolean cleanSession = true;

    /**
     * 保活时间（秒）
     */
    @Min(value = 0, message = "保活时间必须大于0")
    private int keepAliveInSec = 120;

    /**
     * ACK超时时间（秒）
     */
    @Min(value = 0, message = "ACK超时时间必须大于0")
    private int ackTimeoutInSec = 120;

    /**
     * 最大重连尝试次数
     */
    @Min(value = 0, message = "最大重连尝试次数不能小于0")
    private int reconnectMaxAttempts = 10;

    /**
     * 重连间隔（毫秒）
     */
    @Min(value = 0, message = "重连间隔必须大于0")
    private int reconnectIntervalInMs = 5000;

    /**
     * 连接超时时间（毫秒）
     */
    @Min(value = 0, message = "连接超时时间必须大于0")
    private int connectTimeoutInMs = 10000;

    /**
     * 最大飞行队列大小
     */
    @Min(value = 0, message = "最大飞行队列大小必须大于0")
    private int maxInflightQueue = 200;

    /**
     * 总客户端数量
     */
    @Min(value = 1, message = "总客户端数量至少为1")
    private int totalClientCount = 1;

    /**
     * 分发倍数
     */
    @Min(value = 1, message = "分发倍数至少为1")
    private int fanOut = 1;

    /**
     * 聚合倍数
     */
    @Min(value = 1, message = "聚合倍数至少为1")
    private int fanIn = 1;


    /**
     * 主题
     */
    private String topic;

    /**
     * QoS等级
     */
    private MqttQoS qos = MqttQoS.AT_MOST_ONCE;

    /**
     * 是否固定主题
     */
    private boolean fixedTopic = false;

    /**
     * 是否使用通配符主题
     */
    private boolean isWildcard = false;

    /**
     * 消息大小（字节）
     */
    @Min(value = 1, message = "消息大小至少为1字节")
    private int messageSize = 32;

    /**
     * 发布间隔（毫秒）
     */
    @Min(value = 0, message = "发布间隔不能小于0")
    private int pubIntervalInMs = 10000;

    /**
     * 压力测试持续时间（秒）
     */
    @Min(value = 0, message = "持续时间不能小于0")
    private int stressDurationInSec = 60;

    /**
     * 阶段超时时间（秒）
     */
    @Min(value = 0, message = "阶段超时时间必须大于0")
    private int stageTimeoutInSec = 30;

    /**
     * 准备完成后延迟时间（秒）
     */
    @Min(value = 0, message = "延迟时间不能小于0")
    private int delayAfterReadyInSec = 1;

    /**
     * 跳过统计周期
     */
    @Min(value = 0, message = "跳过统计周期不能小于0")
    private int skipStatsPeriod = 0;

    /**
     * 是否保留消息
     */
    private boolean retain = false;

    /**
     * 是否使用MQTT 5.0
     */
    private boolean isMqtt5 = false;

    /**
     * 认证类型
     */
    private String authType = "normal";

    /**
     * 是否使用空客户端ID
     */
    private boolean isEmptyClientId = false;

    /**
     * 过期间隔（秒）
     */
    @Min(value = 0, message = "过期间隔必须大于0")
    private long expiryIntervalInSec = 120;

    /**
     * 仅发布模式
     */
    private boolean pubOnly = false;

    /**
     * 仅订阅模式
     */
    private boolean subOnly = false;

    /**
     * 标签周期间隔（秒）,周期性打印当前的任务状态
     */
    @Min(value = 0, message = "标签周期间隔必须大于0")
    private int tagPeriodIntervalInSec = 30;

    @Min(value = 0, message = "连接速率必须大于0")
    private int connectRate = 500;

    @Min(value = 0, message = "断开连接速率必须大于0")
    private int disconnectRate = 500;

    /**
     * 生命周期动作，例如在连接后立即发送一个消息
     * 动作列表：
     *   pubPostConn
     */
    private String[] lifecycleActions;

    private Map<String, Object> lifecycleActionsConfig = new HashMap<>();


    private WillConfig willConfig = new WillConfig();

    private Boolean exceptionEnds = false;

    /**
     * 转换为TaskConfig对象
     */
    public TaskConfig toTaskConfig() {
        TaskConfig config = new TaskConfig();
        config.setTaskType(this.taskType);
        config.setProtocol(this.protocol);
        config.setBrokers(this.brokers.stream()
                .map(r-> TaskBroker.builder().host(r.getHost()).port(r.getPort()).build()).toList());
        config.setUsername(this.username);
        config.setPassword(this.password);
        config.setTenantId(this.tenantId);
        config.setThingIdStartAt(this.thingIdStartAt);
        config.setThingIdPrefix(this.thingIdPrefix);
        config.setCleanSession(this.cleanSession);
        config.setLocalAddresses(this.localAddresses);
        config.setKeepAliveInSec(this.keepAliveInSec);
        config.setAckTimeoutInSec(this.ackTimeoutInSec);
        config.setReconnectMaxAttempts(this.reconnectMaxAttempts);
        config.setReconnectIntervalInMs(this.reconnectIntervalInMs);
        config.setConnectTimeoutInMs(this.connectTimeoutInMs);
        config.setMaxInflightQueue(this.maxInflightQueue);
        config.setTotalClientCount(this.totalClientCount);
        config.setFanOut(this.fanOut);
        config.setFanIn(this.fanIn);
        config.setTopic(this.topic);
        config.setQos(this.qos);
        config.setFixedTopic(this.fixedTopic);
        config.setWildcard(this.isWildcard);
        config.setMessageSize(this.messageSize);
        config.setPubIntervalInMs(this.pubIntervalInMs);
        config.setStressDurationInSec(this.stressDurationInSec);
        config.setStageTimeoutInSec(this.stageTimeoutInSec);
        config.setDelayAfterReadyInSec(this.delayAfterReadyInSec);
        config.setSkipStatsPeriod(this.skipStatsPeriod);
        config.setRetain(this.retain);
        config.setMqtt5(this.isMqtt5);
        config.setAuthType(this.authType);
        config.setEmptyClientId(this.isEmptyClientId);
        config.setExpiryIntervalInSec(this.expiryIntervalInSec);
        config.setPubOnly(this.pubOnly);
        config.setSubOnly(this.subOnly);
        config.setConnectRate(this.connectRate);
        config.setDisconnectRate(this.disconnectRate);
        config.setTagPeriodIntervalInSec(this.tagPeriodIntervalInSec);
        config.setWillConfig(willConfig);
        config.setLifecycleActions(lifecycleActions);
        config.setLifecycleActionsConfig(this.lifecycleActionsConfig);
        config.setExceptionEnds(this.exceptionEnds);
        config.setEnableAutoMultiAddress(this.autoMultiAddress);
        return config;
    }
}