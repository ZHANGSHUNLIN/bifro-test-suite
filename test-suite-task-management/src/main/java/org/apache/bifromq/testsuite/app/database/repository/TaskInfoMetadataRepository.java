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

import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import java.time.LocalDateTime;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TaskInfoMetadataRepository extends ReactiveMongoRepository<TaskInfoMetadata, String> {

    @Query("{ '_id' : ?0 }")
    @Update(value = "{ $set: { 'taskConfig': ?1 } }")
    Mono<Void> updateTaskConfigById(String id, TaskConfig taskConfig);
    
    @Query("{ '_id' : ?0 }")
    @Update(value = "{ $set: { 'startTime': ?1 } }")
    Mono<Void> updateStartTimeById(String id, LocalDateTime startTime);
    
    @Query("{ '_id' : ?0 }")
    @Update(value = "{ $set: { 'endTime': ?1 } }")
    Mono<Void> updateEndTimeById(String id, LocalDateTime endTime);
    
    @Query("{ '_id' : ?0 }")
    @Update(value = "{ $set: { 'taskConfig.taskWorkStage': ?1, 'currentStage': ?1, 'stageUpdatedAt': ?2 } }")
    Mono<Void> updateStageById(String id, String stage, java.time.Instant updatedAt);
    
    default Mono<TaskInfoMetadata> findByTaskName(String taskName) {
        return findAll().filter(t -> taskName.equals(t.getTaskName())).next();
    }
    
    Flux<TaskInfoMetadata> findByGroup(String groupId);
    
    @Query("{ $or: [ " +
        "{ 'taskConfig.profileConfig.profileId': ?0 }, " +
        "{ 'taskConfig.connectProfileId': ?0 }, " +
        "{ 'taskConfig.disconnectProfileId': ?0 }, " +
        "{ 'taskConfig.subscribeProfileId': ?0 } " +
        "] }")
    Flux<TaskInfoMetadata> findByProfileId(String profileId);

    @Query("{ 'taskConfig.clientCertId': ?0 }")
    Flux<TaskInfoMetadata> findByClientCertId(String clientCertId);
}
