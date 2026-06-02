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

package org.apache.bifromq.testsuite.app.task.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.bifromq.testsuite.app.bean.diagnostics.TaskLogSummaryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskLogSummaryServiceTest {
    @TempDir
    private Path tempDir;

    private final TaskLogSummaryService service = new TaskLogSummaryService();

    @AfterEach
    void tearDown() {
        System.clearProperty("LOG_DIR");
        System.clearProperty("LOG_PATH");
    }

    @Test
    void buildSummary_shouldReadWhitelistedTaskLinesAndApplyLimit() throws Exception {
        Path logDir = tempDir.resolve("logs");
        Path taskLogBase = tempDir.resolve("task-logs");
        Files.createDirectories(logDir);
        Files.createDirectories(taskLogBase.resolve("task"));
        System.setProperty("LOG_DIR", logDir.toString());
        System.setProperty("LOG_PATH", taskLogBase.toString());

        Files.write(logDir.resolve("task-pipeline.log"), List.of(
            "taskId=task-1 stage=start",
            "taskId=other stage=start",
            "taskId=task-1 stage=end"));
        Files.write(logDir.resolve("conn.log"), List.of("taskId=task-1 reason=connect_timeout"));
        Files.write(taskLogBase.resolve("task").resolve("task-1.log"), List.of("task task-1 detail line"));

        TaskLogSummaryResponse summary = service.buildSummary("task-1", 3);

        assertThat(summary.getFiles())
            .containsExactly("task-pipeline.log", "conn.log", "task/task-1.log");
        assertThat(summary.getLines())
            .extracting("line")
            .containsExactly(
                "taskId=task-1 stage=end",
                "taskId=task-1 reason=connect_timeout",
                "task task-1 detail line");
    }

    @Test
    void logCandidates_shouldNotBuildTaskFilePathForUnsafeTaskId() {
        System.setProperty("LOG_DIR", tempDir.resolve("logs").toString());
        System.setProperty("LOG_PATH", tempDir.resolve("task-logs").toString());

        assertThat(service.logCandidates("../task-1"))
            .extracting(path -> path.getFileName().toString())
            .doesNotContain("../task-1.log", "task-1.log");
    }

    @Test
    void normalizeLineLimit_shouldClampRange() {
        assertThat(service.normalizeLineLimit(null)).isEqualTo(200);
        assertThat(service.normalizeLineLimit(0)).isEqualTo(1);
        assertThat(service.normalizeLineLimit(1200)).isEqualTo(1000);
    }
}
