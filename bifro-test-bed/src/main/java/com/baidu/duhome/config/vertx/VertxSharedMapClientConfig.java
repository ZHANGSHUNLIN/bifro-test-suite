package com.baidu.duhome.config.vertx;

import com.baidu.iot.test.suite.HazelcastDataManager;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Slf4j
@Configuration
public class VertxSharedMapClientConfig {

    @Bean
    public HazelcastDataManager hazelcastDataManager(Vertx vertx) {
        return new HazelcastDataManager(vertx);
    }

}
