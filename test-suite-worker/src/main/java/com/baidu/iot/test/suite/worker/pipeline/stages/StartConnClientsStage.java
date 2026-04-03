
package com.baidu.iot.test.suite.worker.pipeline.stages;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.baidu.iot.test.suite.ClientTask;
import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.models.ClientTaskEvent;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;

import com.google.common.util.concurrent.RateLimiter;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;

import lombok.extern.slf4j.Slf4j;

/**
 * Stage for starting connection clients.
 */
@Slf4j
public class StartConnClientsStage extends BaseConnClientsStage {

    private final String clientTag;

    public StartConnClientsStage(String clientTag) {
        this.clientTag = clientTag;
    }


    @Override
    public String getName() {
        return "StartConnClients";
    }

    Map<String, ClientTask> taskClientMap(PipelineContext context) {
        Object connClients = context.getStageData().get(clientTag);
        assert connClients != null : "connClients should not be null";
        return (Map<String, ClientTask>) connClients;
    }

}
