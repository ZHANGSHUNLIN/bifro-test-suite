package com.baidu.duhome;

import com.baidu.duhome.agent.AssistAndEnhanceManagers;
import com.baidu.iot.test.suite.metric.MetricsHelper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class App {

    public static void main(String[] args) {
        MetricsHelper.init(Metrics.globalRegistry);
        SpringApplication.run(App.class, args);

//        AssistAndEnhanceManagers.doEnhance();
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> meterRegistryMeterRegistryCustomizer() {
        return registry -> {

        };
    }

}
