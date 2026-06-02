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

package org.apache.bifromq.testsuite.worker.type;

import java.util.List;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.statemachine.StateMachine;
import org.apache.bifromq.testsuite.worker.context.TaskExecutionContext;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;

public record ExecutionPlan(
    List<PipelineStage<TaskPipelineContext>> stages,
    StateMachine<TaskStage, TaskEvent> stateMachine,
    TaskExecutionContext executionContext
) {
}
