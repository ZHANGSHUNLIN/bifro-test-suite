package com.baidu.duhome.config.vertx;


import io.vertx.core.VertxOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "vertx")
public final class VertxProperties {

    private  String env;
    private String host;
    private boolean multicast;
    private String members;

    private VertxOptions vertxOptions = new VertxOptions();

}