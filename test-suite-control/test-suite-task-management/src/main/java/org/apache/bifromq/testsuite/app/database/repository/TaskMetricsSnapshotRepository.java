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

package org.apache.bifromq.testsuite.app.database.repository;

import org.apache.bifromq.testsuite.app.database.pojo.TaskMetricsSnapshot;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TaskMetricsSnapshotRepository extends ReactiveMongoRepository<TaskMetricsSnapshot, String> {

    Mono<TaskMetricsSnapshot> findFirstByTaskIdOrderByCreateTimeDesc(String taskId);

    Flux<TaskMetricsSnapshot> findAllByTaskIdOrderByCreateTimeDesc(String taskId);

    Mono<TaskMetricsSnapshot> findFirstByTaskIdAndNodeIdAndTaskWorkStageOrderByCreateTimeDesc(
        String taskId, String nodeId, String taskWorkStage);

    Mono<Void> deleteByTaskId(String taskId);

    Flux<TaskMetricsSnapshot> findByTaskNameContaining(String taskName);
}
