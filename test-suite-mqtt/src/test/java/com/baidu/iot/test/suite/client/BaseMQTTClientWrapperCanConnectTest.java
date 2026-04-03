package com.baidu.iot.test.suite.client;

import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ConnectionStatus;
import com.baidu.iot.test.suite.models.TopicFilter;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BaseMQTTClientWrapper#canConnect(TaskStage)}
 */
@ExtendWith(MockitoExtension.class)
class BaseMQTTClientWrapperCanConnectTest {

    private Vertx vertx;
    private MqttClientConfig mqttClientConfig;
    private ClientTaskConfig clientTaskConfig;
    private AtomicReference<TaskStage> taskStage;
    private TestWrapper testWrapper;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        mqttClientConfig = MqttClientConfig.builder()
                .clientId("test-client-123")
                .host("localhost")
                .port(1883)
                .build();

        clientTaskConfig = new ClientTaskConfig();
        clientTaskConfig.setTaskId("test-task");
        taskStage = new AtomicReference<>();

        testWrapper = new TestWrapper(vertx, mqttClientConfig, clientTaskConfig, taskStage);
    }

    @Test
    void testCanConnect_INIT_PUB_CLIENT_shouldReturnTrue() {
        // Given
        taskStage.set(TaskStage.INIT_PUB_CLIENT);

        // When
        boolean result = testWrapper.canConnect(TaskStage.INIT_PUB_CLIENT);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testCanConnect_INIT_SUB_CLIENT_shouldReturnTrue() {
        // Given
        taskStage.set(TaskStage.INIT_SUB_CLIENT);

        // When
        boolean result = testWrapper.canConnect(TaskStage.INIT_SUB_CLIENT);

        // Then
        assertThat(result).isTrue();
    }


    @Test
    void testCanConnect_OngoingStage_shouldReturnTrue() {
        // Given
        taskStage.set(TaskStage.ONGOING);

        // When
        boolean result = testWrapper.canConnect(TaskStage.ONGOING);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testCanConnect_START_shouldReturnTrue() {
        // Given
        taskStage.set(TaskStage.START);

        // When
        boolean result = testWrapper.canConnect(TaskStage.START);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testCanConnect_INIT_shouldReturnTrue() {
        // Given
        taskStage.set(TaskStage.INIT);

        // When
        boolean result = testWrapper.canConnect(TaskStage.INIT);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testCanConnect_SHUTDOWN_shouldReturnFalse() {
        // Given
        taskStage.set(TaskStage.SHUTDOWN);

        // When
        boolean result = testWrapper.canConnect(TaskStage.SHUTDOWN);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testCanConnect_STOPPED_shouldReturnFalse() {
        // Given
        taskStage.set(TaskStage.STOPPED);

        // When
        boolean result = testWrapper.canConnect(TaskStage.STOPPED);

        // Then
        assertThat(result).isFalse();
    }

    /**
     * Test wrapper class to expose protected canConnect method for testing
     */
    private static class TestWrapper extends BaseMQTTClientWrapper {

        public TestWrapper(Vertx vertx, MqttClientConfig clientConfig,
                          ClientTaskConfig taskConfig, AtomicReference<TaskStage> taskStage) {
            super(vertx, clientConfig, taskConfig, taskStage);
        }


        @Override
        public String getClientId() {
            return clientConfig.getClientId();
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public ConnectionStatus getStatus() {
            return ConnectionStatus.INIT;
        }


        @Override
        public CompletableFuture<List<Integer>> subscribe(Set<TopicFilter> topicFilters) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        CompletableFuture<Void> internalConnect() {
            return null;
        }

        @Override
        public CompletableFuture<Void> unsubscribeAll() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> publish(byte[] payload, String topic, int qos, boolean isDup, boolean isRetain) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> disconnect() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> close() {
            return CompletableFuture.completedFuture(null);
        }
    }
}
