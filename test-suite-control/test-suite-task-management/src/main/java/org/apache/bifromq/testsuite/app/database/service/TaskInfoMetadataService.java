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

package org.apache.bifromq.testsuite.app.database.service;

import com.mongodb.client.result.UpdateResult;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnControlPlane
public class TaskInfoMetadataService {

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @Resource
    private ReactiveMongoTemplate reactiveMongoTemplate;

    public Mono<TaskInfoMetadata> insertTaskInfoMetadata(TaskInfoMetadata taskInfoMetadata) {
        return taskInfoMetadataRepository.insert(taskInfoMetadata);
    }

    public Mono<Page<TaskInfoMetadata>> findAll(Pageable pageable) {
        Query query = new Query();
        query.with(pageable);
        return reactiveMongoTemplate.find(query, TaskInfoMetadata.class)
            .collectList()
            .zipWith(reactiveMongoTemplate.count(Query.of(query).limit(-1).skip(-1), TaskInfoMetadata.class))
            .map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
    }

    public Mono<Page<TaskInfoMetadata>> findByFilters(String taskName, String taskType, String group, String status,
                                                      Pageable pageable) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (taskName != null && !taskName.isEmpty()) {
            criteriaList.add(Criteria.where("taskName").regex(taskName, "i"));
        }

        if (taskType != null && !taskType.isEmpty()) {
            criteriaList.add(Criteria.where("taskConfig.taskType").is(taskType));
        }
        
        if (group != null && !group.isEmpty()) {
            criteriaList.add(Criteria.where("group").is(group));
        }
        
        if (status != null && !status.isEmpty()) {
            criteriaList.add(Criteria.where("currentStage").is(status));
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        query.with(pageable);
        return reactiveMongoTemplate.find(query, TaskInfoMetadata.class)
            .collectList()
            .zipWith(reactiveMongoTemplate.count(Query.of(query).limit(-1).skip(-1), TaskInfoMetadata.class))
            .map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
    }

    public Mono<Boolean> tryUpdateStage(String taskId, TaskStage expectedStage, TaskStage nextStage,
                                        Instant updatedAt) {
        return tryUpdateStage(taskId, List.of(expectedStage), nextStage, updatedAt);
    }

    public Mono<Boolean> tryUpdateStage(String taskId, Collection<TaskStage> expectedStages, TaskStage nextStage,
                                        Instant updatedAt) {
        if (taskId == null || taskId.isBlank() || expectedStages == null || expectedStages.isEmpty()
            || nextStage == null) {
            return Mono.just(false);
        }
        List<TaskStage> stages = expectedStages.stream()
            .filter(stage -> stage != null)
            .toList();
        if (stages.isEmpty()) {
            return Mono.just(false);
        }
        Criteria stageMatches = new Criteria().orOperator(
            Criteria.where("currentStage").in(stages),
            new Criteria().andOperator(
                Criteria.where("currentStage").exists(false),
                Criteria.where("taskConfig.taskWorkStage").in(stages)
            ),
            new Criteria().andOperator(
                Criteria.where("currentStage").is(null),
                Criteria.where("taskConfig.taskWorkStage").in(stages)
            )
        );
        Query query = Query.query(Criteria.where("_id").is(taskId).andOperator(stageMatches));
        Update update = new Update()
            .set("currentStage", nextStage)
            .set("taskConfig.taskWorkStage", nextStage)
            .set("stageUpdatedAt", updatedAt == null ? Instant.now() : updatedAt);
        return reactiveMongoTemplate.updateFirst(query, update, TaskInfoMetadata.class)
            .map(UpdateResult::getModifiedCount)
            .map(count -> count > 0);
    }
}
