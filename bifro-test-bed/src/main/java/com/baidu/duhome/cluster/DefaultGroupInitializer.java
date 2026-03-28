package com.baidu.duhome.cluster;

import com.baidu.duhome.database.pojo.MqttGroup;
import com.baidu.duhome.database.repository.MqttGroupRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 默认分组初始化器
 * 应用启动时检查是否存在"默认分组"，不存在则创建
 */
@Slf4j
@Component
public class DefaultGroupInitializer implements ApplicationRunner {

    @Resource
    private MqttGroupRepository groupRepository;

    @Override
    public void run(ApplicationArguments args) {
        groupRepository.findByName("默认分组")
                .switchIfEmpty(
                        groupRepository.save(MqttGroup.builder()
                                        .name("默认分组")
                                        .description("系统默认分组")
                                        .createdAt(Instant.now())
                                        .updatedAt(Instant.now())
                                        .build())
                )
                .doOnNext(group -> log.info("默认分组已创建: {}", group.getName()))
                .subscribe();
    }
}
