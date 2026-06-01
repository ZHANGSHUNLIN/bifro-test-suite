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

package org.apache.bifromq.testsuite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TaskStageTest {

    @Test
    void taskStage_shouldHaveExactlyNineStages() {
        assertThat(TaskStage.values()).hasSize(9);
    }

    @Test
    void taskStage_values_shouldContainAllLifecycleStages() {
        assertThat(TaskStage.values()).containsExactlyInAnyOrder(
            TaskStage.INIT,
            TaskStage.ASSIGNED,
            TaskStage.STARTING,
            TaskStage.ONGOING,
            TaskStage.SHUTTING,
            TaskStage.SHUTDOWN,
            TaskStage.STOPPED,
            TaskStage.FAILED,
            TaskStage.TIMEOUT
        );
    }

    @Test
    void taskStage_terminalStages_shouldBeCorrect() {
        
        assertThat(TaskStage.SHUTDOWN).isNotNull();
        assertThat(TaskStage.STOPPED).isNotNull();
        assertThat(TaskStage.FAILED).isNotNull();
        assertThat(TaskStage.TIMEOUT).isNotNull();
    }

    @Test
    void taskStage_name_shouldReturnCorrectName() {
        assertThat(TaskStage.INIT.name()).isEqualTo("INIT");
        assertThat(TaskStage.ASSIGNED.name()).isEqualTo("ASSIGNED");
        assertThat(TaskStage.STARTING.name()).isEqualTo("STARTING");
        assertThat(TaskStage.ONGOING.name()).isEqualTo("ONGOING");
        assertThat(TaskStage.SHUTTING.name()).isEqualTo("SHUTTING");
        assertThat(TaskStage.SHUTDOWN.name()).isEqualTo("SHUTDOWN");
        assertThat(TaskStage.STOPPED.name()).isEqualTo("STOPPED");
        assertThat(TaskStage.FAILED.name()).isEqualTo("FAILED");
        assertThat(TaskStage.TIMEOUT.name()).isEqualTo("TIMEOUT");
    }

    @Test
    void taskStage_valueOf_shouldReturnCorrectStage() {
        assertThat(TaskStage.valueOf("INIT")).isEqualTo(TaskStage.INIT);
        assertThat(TaskStage.valueOf("ASSIGNED")).isEqualTo(TaskStage.ASSIGNED);
        assertThat(TaskStage.valueOf("ONGOING")).isEqualTo(TaskStage.ONGOING);
        assertThat(TaskStage.valueOf("SHUTDOWN")).isEqualTo(TaskStage.SHUTDOWN);
    }

    @Test
    void taskStage_removedIntermediateStages_shouldNotExist() {
        
        List<String> stageNames = Arrays.stream(TaskStage.values())
            .map(Enum::name)
            .collect(Collectors.toList());

        assertThat(stageNames).doesNotContain("START");         
        assertThat(stageNames).doesNotContain("INIT_CLIENT");
        assertThat(stageNames).doesNotContain("INIT_PUB_CLIENT");
        assertThat(stageNames).doesNotContain("INIT_SUB_CLIENT");
        assertThat(stageNames).doesNotContain("PUB_SUB_CLIENT_READY");
        assertThat(stageNames).doesNotContain("PUB_SUB_CLIENT_START");
        assertThat(stageNames).doesNotContain("PUB_CLIENT_CONN");
        assertThat(stageNames).doesNotContain("SUB_CLIENT_CONN");
    }
}
