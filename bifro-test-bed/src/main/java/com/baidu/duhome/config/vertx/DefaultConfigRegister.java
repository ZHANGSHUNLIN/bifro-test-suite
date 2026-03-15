package com.baidu.duhome.config.vertx;

import com.baidu.duhome.database.pojo.MqttBroker;
import com.baidu.duhome.config.broker.DefaultMqttBrokerProperties;
import com.baidu.iot.test.suite.ShareDataAddr;
import com.baidu.iot.test.suite.ShareDataManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class DefaultConfigRegister {

    @Resource
    private DefaultMqttBrokerProperties defaultMqttBrokerProperties;

    @Resource
    private ShareDataManager shareDataManager;


    @PostConstruct
    public void init() {
        // default broker config.
        addDefaultBroker();
    }


    public void addDefaultBroker() {
        List<MqttBroker> clusterMembers = defaultMqttBrokerProperties.getClusterMembers();
        clusterMembers.forEach(mqttBroker -> {
            log.info("add default broker: {}", mqttBroker);
            shareDataManager.<String, MqttBroker>map(ShareDataAddr.BROKER_MAP_NAME)
                    .key(mqttBroker.getBrokerId())
                    .putIfAbsent(mqttBroker);
        });
    }

}
