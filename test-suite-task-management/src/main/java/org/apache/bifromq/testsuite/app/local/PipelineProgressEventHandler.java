/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.app.local;

import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.eventbus.EventBusAddresses;
import org.apache.bifromq.testsuite.pipeline.PipelineProgressEvent;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnControlPlane
public class PipelineProgressEventHandler {

    @Resource
    private Vertx vertx;

    @Resource
    private ReactiveMongoTemplate mongoTemplate;

    @PostConstruct
    public void register() {
        vertx.eventBus().<PipelineProgressEvent>consumer(
            EventBusAddresses.PIPELINE_PROGRESS,
            message -> handleProgressEvent(message.body())
        );
        log.info("PipelineProgressEventHandler registered for {}", EventBusAddresses.PIPELINE_PROGRESS);
    }

    private void handleProgressEvent(PipelineProgressEvent event) {
        if (event == null || event.getTaskId() == null || event.getNodeId() == null) {
            log.warn("Received null or incomplete PipelineProgressEvent, skipping");
            return;
        }

        log.debug("Received pipeline progress event: taskId={}, nodeId={}, stages={}",
            event.getTaskId(), event.getNodeId(),
            event.getStages() != null ? event.getStages().size() : 0);

        vertx.executeBlocking(() -> {
            try {
                Query query = Query.query(
                    Criteria.where("taskId").is(event.getTaskId())
                        .and("nodeId").is(event.getNodeId())
                );
                Update update = Update.update("pipelineStages", event.getStages());
                mongoTemplate.updateFirst(query, update, NodeTask.class).block();
                log.debug("Pipeline stages persisted: taskId={}, nodeId={}",
                    event.getTaskId(), event.getNodeId());
            } catch (Exception e) {
                log.error("Failed to persist pipeline progress: taskId={}, nodeId={}",
                    event.getTaskId(), event.getNodeId(), e);
            }
            return null;
        });
    }
}
