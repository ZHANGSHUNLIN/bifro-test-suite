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

package org.apache.bifromq.testsuite.app.controller.node;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.bean.NodeInfo;
import org.apache.bifromq.testsuite.app.bean.vo.NodeListVO;
import org.apache.bifromq.testsuite.app.bean.vo.NodeTaskVO;
import org.apache.bifromq.testsuite.app.cluster.core.ClientInstanceService;
import org.apache.bifromq.testsuite.app.cluster.core.ClusterDataManager;
import org.apache.bifromq.testsuite.app.cluster.core.NodeMetricsService;
import org.apache.bifromq.testsuite.app.controller.ApiController;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.repository.NodeTaskRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.metric.NodeMetricsResponse;
import org.apache.bifromq.testsuite.web.ApiException;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryResponse;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@RestController
@RequestMapping("/api/node")
@Tag(name = "Node Management", description = "Cluster node info query and management API")
public class NodeController implements ApiController {

    @Resource
    private ClusterDataManager clusterDataManager;

    @Resource
    private NodeMetricsService nodeMetricsService;

    @Resource
    private ClientInstanceService clientInstanceService;

    @Resource
    private NodeTaskRepository nodeTaskRepository;

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @Operation(summary = "Get All Nodes", description = "Get status and resource info of all cluster nodes")
    @GetMapping("/allNodes")
    public Mono<ApiResponse<List<NodeListVO>>> allNodes() {
        return Mono.fromCompletionStage(clusterDataManager.allMemberNodes())
            .timeout(Duration.ofSeconds(5))
            .map(nodeMap -> nodeMap.entrySet().stream()
                .map(e -> NodeListVO.fromNodeInfo(e.getKey(), e.getValue()))
                .collect(Collectors.toList()))
            .map(ApiResponse::success)
            .onErrorResume(TimeoutException.class, e -> {
                log.warn("Timed out while loading cluster nodes", e);
                return Mono.just(ApiResponse.error(Messages.get("error.data.timeout")));
            })
            .onErrorResume(e -> {
                log.error("Failed to load cluster nodes", e);
                return Mono.just(ApiResponse.error(Messages.get("error.data.timeout")));
            });
    }

    @Operation(summary = "Get Node Details", description = "Get node detailed status and resource info by node ID")
    @GetMapping("/{nodeId}")
    public Mono<ApiResponse<NodeListVO>> getNode(
        @PathVariable(value = "nodeId") @Parameter(description = "Node ID") String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return Mono.just(ApiResponse.error(Messages.get("error.node.idNotEmpty")));
        }
        return Mono.fromCompletionStage(clusterDataManager.allNodes())
            .map(nodeMap -> {
                NodeInfo nodeInfo = nodeMap.get(nodeId);
                if (nodeInfo == null) {
                    throw new ApiException(Messages.get("error.node.notFound", nodeId));
                }
                return NodeListVO.fromNodeInfo(nodeId, nodeInfo);
            })
            .map(ApiResponse::success);
    }

    @Operation(summary = "Query Node Metrics", description = "Query real-time running metrics of a node via EventBus")
    @GetMapping("/metrics")
    public Mono<ApiResponse<NodeMetricsResponse>> getNodeMetrics(
        @RequestParam(value = "nodeId") @Parameter(description = "Node ID") String nodeId,
        @RequestParam(value = "taskId", required = false)
        @Parameter(description = "Task ID (optional, if not provided queries node global metrics)") String taskId,
        @RequestParam(value = "metricNames", required = false)
        @Parameter(description = "Metric name list (optional, comma-separated)") List<String> metricNames) {
        return Mono.fromCallable(() ->
                nodeMetricsService.queryNodeMetrics(nodeId, taskId, metricNames))
            .subscribeOn(Schedulers.boundedElastic())
            .map(ApiResponse::success);
    }

    @Operation(summary = "Query Client Instances", description = "Query paginated list of client instances running on a node")
    @GetMapping("/{nodeId}/clients")
    public Mono<ApiResponse<ClientQueryResponse>> getClientInstances(
        @PathVariable(value = "nodeId") @Parameter(description = "Node ID") String nodeId,
        @RequestParam(value = "taskId") @Parameter(description = "Task ID") String taskId,
        @RequestParam(value = "clientType", defaultValue = "conn")
        @Parameter(description = "Client type: conn/pub/sub") String clientType,
        @RequestParam(value = "page", defaultValue = "0")
        @Parameter(description = "Page number (0-indexed)") int page,
        @RequestParam(value = "size", defaultValue = "20")
        @Parameter(description = "Page size") int size) {
        return Mono.fromCallable(() ->
                clientInstanceService.queryClientInstances(nodeId, taskId, clientType, page, size))
            .subscribeOn(Schedulers.boundedElastic())
            .map(ApiResponse::success);
    }

    @Operation(summary = "Get Node Task List", description = "Get all tasks running on the specified node")
    @GetMapping("/{nodeId}/tasks")
    public Mono<ApiResponse<List<NodeTaskVO>>> getNodeTasks(
        @PathVariable(value = "nodeId") @Parameter(description = "Node ID") String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return Mono.just(ApiResponse.error(Messages.get("error.node.idNotEmpty")));
        }
        return nodeTaskRepository.findAllByNodeId(nodeId)
            .flatMap(nodeTask -> taskInfoMetadataRepository.findById(nodeTask.getTaskId())
                .map(metadata -> convertToVO(nodeTask, metadata.getTaskName()))
                .defaultIfEmpty(convertToVO(nodeTask, null)))
            .collectList()
            .map(ApiResponse::success);
    }

    @Operation(summary = "Subscribe to task node metrics stream", description = "SSE push: aggregate metrics for all task nodes pushed every 2 seconds")
    @GetMapping(value = "/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<List<NodeMetricsResponse>>> streamTaskMetrics(
        @RequestParam("taskId") @Parameter(description = "Task ID") String taskId,
        @RequestParam("nodeIds") @Parameter(description = "Node ID list, comma-separated") String nodeIds) {
        List<String> ids = List.of(nodeIds.split(","));
        return Flux.interval(Duration.ZERO, Duration.ofSeconds(2))
            .flatMap(tick -> Flux.fromIterable(ids)
                .flatMap(nodeId -> Mono.fromCallable(
                        () -> nodeMetricsService.queryNodeMetrics(nodeId, taskId, null))
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorReturn(NodeMetricsResponse.builder()
                        .nodeId(nodeId)
                        .success(false)
                        .counterMetrics(java.util.Collections.emptyList())
                        .timerMetrics(java.util.Collections.emptyList())
                        .build()))
                .collectList())
            .map(data -> ServerSentEvent.<List<NodeMetricsResponse>>builder()
                .data(data)
                .build());
    }

    private NodeTaskVO convertToVO(NodeTask nodeTask, String taskName) {
        return NodeTaskVO.builder()
            .taskId(nodeTask.getTaskId())
            .taskName(taskName)
            .currentStage(nodeTask.getCurrentStage())
            .totalClientCount(nodeTask.getTaskConfig() != null
                ? nodeTask.getTaskConfig().getTotalClientCount() : 0)
            .nodeId(nodeTask.getNodeId())
            .nodeName(nodeTask.getNodeName())
            .build();
    }
}
