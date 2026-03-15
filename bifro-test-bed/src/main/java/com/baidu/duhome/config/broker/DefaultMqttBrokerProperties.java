package com.baidu.duhome.config.broker;

import com.baidu.duhome.database.pojo.MqttBroker;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "bifro.mqtt.default")
public final class DefaultMqttBrokerProperties {

    private List<MqttBroker> clusterMembers = new ArrayList<>();

}