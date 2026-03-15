// SharedMapClient.java
package com.baidu.iot.test.suite;

import java.util.concurrent.CompletableFuture;

/**
 * 共享Map客户端接口
 * 统一操作异步共享Map，便于测试和替换实现
 */
public interface SharedMapClient {
    
    /**
     * 向指定Map中添加键值对
     * @param mapName Map名称
     * @param key 键
     * @param value 值
     * @return 操作完成的Future，成功返回null，失败包含异常
     */
    <K, V> CompletableFuture<Void> put(String mapName, K key, V value);
    
    /**
     * 从指定Map中获取值
     * @param mapName Map名称
     * @param key 键
     * @return 包含值的Future，如果键不存在返回null
     */
    <K, V> CompletableFuture<V> get(String mapName, K key);
    
    /**
     * 从指定Map中移除键值对
     * @param mapName Map名称
     * @param key 键
     * @return 操作完成的Future，成功返回被移除的值，失败包含异常
     */
    <K, V> CompletableFuture<V> remove(String mapName, K key);
    
    /**
     * 获取整个Map实例（高级操作）
     * @param mapName Map名称
     * @return 包含AsyncMap的Future
     */
    <K, V> CompletableFuture<io.vertx.core.shareddata.AsyncMap<K, V>> getMap(String mapName);
}