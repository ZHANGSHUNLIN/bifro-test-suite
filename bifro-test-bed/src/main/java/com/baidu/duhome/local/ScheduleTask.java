package com.baidu.duhome.local;

import com.baidu.duhome.bean.NodeInfo;
import com.baidu.duhome.cluster.ClusterDataManager;

import com.baidu.iot.test.suite.HazelcastDataManager;
import io.vertx.core.Vertx;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ScheduleTask {

    @Resource
    private Vertx vertx;

    @Resource
    private HazelcastDataManager hazelcastDataManager;

    @Resource
    private ClusterDataManager clusterDataManager;
    private final int pingTimeOut = 30000;

    @Scheduled(fixedRate = 15_000)
    public void detectionFinishTask() {
        log.debug("定时扫描任务启动");
        pingClusterStatus();
    }


    public void pingClusterStatus() {
        try {
            long l = System.currentTimeMillis();
            String currentNodeIdCache = clusterDataManager.getCurrentNodeIdCache();
            HazelcastDataManager.IMapWrapper<String, NodeInfo> map =
                    hazelcastDataManager.<String, NodeInfo>map(HazelcastDataManager.ShareDataAddr.CLUSTER_NODE_INFO);
            map.key(currentNodeIdCache)
                    .thenAccept((nodeInfo) -> {
                        nodeInfo.setNextPing(l + pingTimeOut);
                        map.key(currentNodeIdCache).
                                replace(nodeInfo).future()
                                .exceptionally(e -> {
                                    log.error("更新节点信息失败", e);
                                    return null;
                                });
                    });
            log.debug("更新当前节点时间,{} , 更新时间:{}", currentNodeIdCache, l);
        } catch (Exception e) {
            log.error("更新当前节点时间失败", e);
        }
    }

}