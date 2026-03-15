package com.baidu.duhome;

import com.baidu.duhome.agent.AssistAndEnhanceManagers;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
//        AssistAndEnhanceManagers.doEnhance();
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> meterRegistryMeterRegistryCustomizer() {
        return registry -> {

        };
    }

}
