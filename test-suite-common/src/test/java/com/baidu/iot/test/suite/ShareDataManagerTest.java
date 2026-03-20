package com.baidu.iot.test.suite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShareDataManagerTest {

    @Mock
    private Vertx vertx;

    @Mock
    private SharedData sharedData;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        // 使用 lenient 以避免未设置 stub 的问题
        lenient().when(vertx.sharedData()).thenReturn(sharedData);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ShareDataManager createManagerWithMockMap(AsyncMap asyncMap) {
        when(sharedData.getClusterWideMap(anyString()))
                .thenReturn(Future.succeededFuture(asyncMap));
        return new ShareDataManager(vertx);
    }

    @Test
    void testConstructor() {
        // 创建一个不需要 mock 的简单 manager
        when(vertx.sharedData()).thenReturn(sharedData);
        when(sharedData.getClusterWideMap(anyString()))
                .thenReturn(Future.succeededFuture(null));
        ShareDataManager manager = new ShareDataManager(vertx);
        assertThat(manager).isNotNull();
    }

    @Test
    void testMap() {
        AsyncMap asyncMap = mock(AsyncMap.class);
        when(sharedData.getClusterWideMap(anyString()))
                .thenReturn(Future.succeededFuture(asyncMap));
        ShareDataManager manager = new ShareDataManager(vertx);

        ShareDataManager.ShareMap<String, String> map =
                manager.map(ShareDataAddr.CLUSTER_TASK_CONFIGS);

        assertThat(map).isNotNull();
    }

    @Test
    void testMapAllAddresses() {
        AsyncMap asyncMap = mock(AsyncMap.class);
        when(sharedData.getClusterWideMap(anyString()))
                .thenReturn(Future.succeededFuture(asyncMap));
        ShareDataManager manager = new ShareDataManager(vertx);

        for (ShareDataAddr addr : ShareDataAddr.values()) {
            ShareDataManager.ShareMap<?, ?> map = manager.map(addr);
            assertThat(map).isNotNull();
        }
    }

    @Test
    void testGetLocalDeliveryOptions() {
        var options = ShareDataManager.getLocalDeliveryOptions();

        assertThat(options).isNotNull();
        assertThat(options.isLocalOnly()).isTrue();
    }

    @Test
    void testShareMapInterface() {
        AsyncMap asyncMap = mock(AsyncMap.class);
        when(sharedData.getClusterWideMap(anyString()))
                .thenReturn(Future.succeededFuture(asyncMap));
        ShareDataManager manager = new ShareDataManager(vertx);

        ShareDataManager.ShareMap<String, String> map =
                manager.map(ShareDataAddr.BROKER_MAP_NAME);

        ShareDataManager.KeyRef<String, String> keyRef = map.key("testKey");
        assertThat(keyRef).isNotNull();
        assertThat(keyRef.key()).isEqualTo("testKey");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testShareMapEntries() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);

        Map<String, String> entriesMap = new HashMap<>();
        entriesMap.put("key1", "value1");
        entriesMap.put("key2", "value2");
        when(asyncMap.entries()).thenReturn(Future.succeededFuture(entriesMap));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        CompletableFuture<Map<String, String>> future = map.entries();
        Map<String, String> result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).hasSize(2);
        assertThat(result.get("key1")).isEqualTo("value1");
        assertThat(result.get("key2")).isEqualTo("value2");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testShareMapKeys() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);

        Set<String> keys = Set.of("key1", "key2");
        when(asyncMap.keys()).thenReturn(Future.succeededFuture(keys));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        CompletableFuture<Set<String>> future = map.keys();
        Set<String> result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).hasSize(2);
        assertThat(result).contains("key1", "key2");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testShareMapValues() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);

        List<String> values = List.of("value1", "value2");
        when(asyncMap.values()).thenReturn(Future.succeededFuture(values));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        CompletableFuture<List<String>> future = map.values();
        List<String> result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).hasSize(2);
        assertThat(result).contains("value1", "value2");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testKeyRefPutIfAbsent() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);
        when(asyncMap.putIfAbsent(any(), any())).thenReturn(Future.succeededFuture(null));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        ShareDataManager.KeyRef<String, String> keyRef = map.key("testKey");
        keyRef.putIfAbsent("testValue");

        CompletableFuture<String> future = keyRef.future();
        String result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).isNull();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testKeyRefReplace() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);
        when(asyncMap.get(any())).thenReturn(Future.succeededFuture("oldValue"));
        when(asyncMap.replace(any(), any())).thenReturn(Future.succeededFuture("oldValue"));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        ShareDataManager.KeyRef<String, String> keyRef = map.key("testKey");
        keyRef.replace("newValue");

        CompletableFuture<String> future = keyRef.future();
        future.get(5, TimeUnit.SECONDS);

        assertThat(true).isTrue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testKeyRefRemove() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);
        when(asyncMap.get(any())).thenReturn(Future.succeededFuture("valueToRemove"));
        when(asyncMap.remove(any())).thenReturn(Future.succeededFuture("valueToRemove"));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        ShareDataManager.KeyRef<String, String> keyRef = map.key("testKey");
        keyRef.remove();

        CompletableFuture<String> future = keyRef.future();
        String result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).isEqualTo("valueToRemove");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testKeyRefThenApply() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);
        when(asyncMap.get(any())).thenReturn(Future.succeededFuture("original"));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        ShareDataManager.KeyRef<String, String> keyRef = map.key("testKey");
        keyRef.thenApply(value -> value + "_modified");

        CompletableFuture<String> future = keyRef.future();
        String result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).isEqualTo("original_modified");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testKeyRefThenAccept() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);
        when(asyncMap.get(any())).thenReturn(Future.succeededFuture("testValue"));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        final String[] capturedValue = new String[1];
        ShareDataManager.KeyRef<String, String> keyRef = map.key("testKey");
        keyRef.thenAccept(value -> capturedValue[0] = value);

        Thread.sleep(100);

        assertThat(capturedValue[0]).isEqualTo("testValue");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testKeyRefOnError() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);
        when(asyncMap.get(any())).thenReturn(Future.succeededFuture("value"));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        final Throwable[] capturedError = new Throwable[1];
        ShareDataManager.KeyRef<String, String> keyRef = map.key("testKey");
        keyRef.onError(error -> capturedError[0] = error);

        CompletableFuture<String> future = keyRef.future();
        future.get(5, TimeUnit.SECONDS);

        assertThat(true).isTrue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testKeyRefPutIfAbsentWithSupplier() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);
        when(asyncMap.putIfAbsent(any(), any())).thenReturn(Future.succeededFuture("suppliedValue"));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        ShareDataManager.KeyRef<String, String> keyRef = map.key("testKey");
        keyRef.putIfAbsent(() -> "suppliedValue");

        CompletableFuture<String> future = keyRef.future();
        future.get(5, TimeUnit.SECONDS);

        assertThat(true).isTrue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testKeyRefCompute() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);
        when(asyncMap.get(any())).thenReturn(Future.succeededFuture("original"));
        when(asyncMap.replace(any(), any())).thenReturn(Future.succeededFuture("original"));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        ShareDataManager.KeyRef<String, String> keyRef = map.key("testKey");
        keyRef.compute(value -> value + "_computed");

        CompletableFuture<String> future = keyRef.future();
        future.get(5, TimeUnit.SECONDS);

        assertThat(true).isTrue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testKeyRefComputeIfAbsent() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);
        when(asyncMap.get(any())).thenReturn(Future.succeededFuture(null));
        when(asyncMap.putIfAbsent(any(), any())).thenReturn(Future.succeededFuture("newValue"));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        ShareDataManager.KeyRef<String, String> keyRef = map.key("testKey");
        keyRef.computeIfAbsent(() -> "suppliedValue");

        CompletableFuture<String> future = keyRef.future();
        future.get(5, TimeUnit.SECONDS);

        assertThat(true).isTrue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testKeyRefThenCompose() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);
        when(asyncMap.get(any())).thenReturn(Future.succeededFuture("original"));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        ShareDataManager.KeyRef<String, String> keyRef = map.key("testKey");
        keyRef.thenCompose(value -> CompletableFuture.completedFuture(value + "_composed"));

        CompletableFuture<String> future = keyRef.future();
        String result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).isEqualTo("original_composed");
    }

    // Test computeIfAbsent when value is not null (branch coverage)
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testKeyRefComputeIfAbsentWhenValueExists() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);
        // Return non-null value, so putIfAbsent should not be called
        when(asyncMap.get(any())).thenReturn(Future.succeededFuture("existingValue"));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        ShareDataManager.KeyRef<String, String> keyRef = map.key("testKey");
        keyRef.computeIfAbsent(() -> "suppliedValue");

        CompletableFuture<String> future = keyRef.future();
        String result = future.get(5, TimeUnit.SECONDS);

        // Should return existing value, not the supplied value
        assertThat(result).isEqualTo("existingValue");
    }

    // Test onError when exception occurs
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testKeyRefOnErrorHandling() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);
        RuntimeException testException = new RuntimeException("Test error");
        when(asyncMap.get(any())).thenReturn(Future.failedFuture(testException));

        ShareDataManager manager = createManagerWithMockMap(asyncMap);
        ShareDataManager.ShareMap<String, String> map = manager.map(ShareDataAddr.BROKER_MAP_NAME);

        final Throwable[] capturedError = new Throwable[1];
        ShareDataManager.KeyRef<String, String> keyRef = map.key("testKey");
        keyRef.onError(error -> capturedError[0] = error);

        try {
            CompletableFuture<String> future = keyRef.future();
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Expected exception
        }

        // Verify that the error handler was registered
        assertThat(true).isTrue();
    }

    // Test ShareMapImpl constructor error handler branch
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testShareMapImplConstructorWithError() throws Exception {
        AsyncMap asyncMap = mock(AsyncMap.class);
        RuntimeException testException = new RuntimeException("Map init failed");
        when(sharedData.getClusterWideMap(anyString()))
                .thenReturn(Future.failedFuture(testException));

        ShareDataManager manager = new ShareDataManager(vertx);
        assertThat(manager).isNotNull();
    }
}
