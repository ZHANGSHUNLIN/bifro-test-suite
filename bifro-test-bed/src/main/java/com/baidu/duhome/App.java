package com.baidu.duhome;

import com.baidu.iot.test.suite.metric.MetricsHelper;
import io.micrometer.core.instrument.Metrics;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(exclude = {HazelcastAutoConfiguration.class})
public class App {

    public static void main(String[] args) {
        MetricsHelper.init(Metrics.globalRegistry);
        SpringApplication.run(App.class, args);
    }

}
