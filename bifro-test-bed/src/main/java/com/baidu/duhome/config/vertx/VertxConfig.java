package com.baidu.duhome.config.vertx;

import com.baidu.duhome.config.vertx.codec.VertxCodecManager;
import com.hazelcast.config.Config;
import com.hazelcast.config.InterfacesConfig;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class VertxConfig {


    @Bean
    public Vertx vertx(VertxProperties vertxProperties) {

        Config hazelcastConfig = new Config();
        hazelcastConfig.setClusterName(vertxProperties.getEnv());

        NetworkConfig networkConfig = hazelcastConfig.getNetworkConfig()
                .setInterfaces(new InterfacesConfig().addInterface(vertxProperties.getHost()))
                .setPortAutoIncrement(true)
                .setReuseAddress(true);
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(vertxProperties.isMulticast());
        joinConfig.getTcpIpConfig()
                .setEnabled(true)
                .addMember(vertxProperties.getMembers());
        HazelcastClusterManager hazelcastClusterManager = new HazelcastClusterManager(hazelcastConfig);

        return Vertx.builder()
                .with(new VertxOptions(vertxProperties.getVertxOptions())
                        .setPreferNativeTransport(true))
                .withClusterManager(hazelcastClusterManager)
                .buildClustered()
                .toCompletionStage()
                .toCompletableFuture()
                .thenApply(vertx -> {
                    vertx.exceptionHandler((e) -> log.error("EventBus error:", e));
                    VertxCodecManager.registerAll(vertx);
                    return vertx;
                })
                .whenComplete((vertx, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to start Test Suite server", throwable);
                        return;
                    }
                    Runtime.getRuntime().addShutdownHook(
                            new Thread(() ->
                                    uninterrupted(() -> vertx.close().toCompletionStage().toCompletableFuture().join())
                            )
                    );
                })
                .join();

    }

    private void uninterrupted(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            log.error("Error during shutdown", e);
        }
    }


}