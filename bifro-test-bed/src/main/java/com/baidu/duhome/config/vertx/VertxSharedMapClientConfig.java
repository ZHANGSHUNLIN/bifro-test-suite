package com.baidu.duhome.config.vertx;

import com.baidu.iot.test.suite.ShareDataManager;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Slf4j
@Configuration
public class VertxSharedMapClientConfig {

    @Bean
    public ShareDataManager vertxSharedMapClient(Vertx vertx) {
        return new ShareDataManager(vertx);
    }

}
