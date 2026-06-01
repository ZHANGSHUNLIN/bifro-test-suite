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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.bifromq.testsuite.app.bean.diagnostics.TaskLogSummaryEntry;
import org.apache.bifromq.testsuite.app.bean.diagnostics.TaskLogSummaryResponse;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class TaskLogSummaryService {
    private static final int DEFAULT_LINES = 200;
    private static final int MAX_LINES = 1000;
    private static final List<String> SHARED_LOG_FILES = List.of(
        "task-pipeline.log",
        "task-state-machine.log",
        "conn.log",
        "error.log"
    );

    public Mono<ApiResponse<TaskLogSummaryResponse>> getLogSummary(String taskId, Integer requestedLines) {
        int limit = normalizeLineLimit(requestedLines);
        return Mono.fromCallable(() -> ApiResponse.success(buildSummary(taskId, limit)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    TaskLogSummaryResponse buildSummary(String taskId, int limit) {
        List<Path> candidates = logCandidates(taskId);
        Set<String> files = new LinkedHashSet<>();
        ArrayDeque<TaskLogSummaryEntry> matched = new ArrayDeque<>();

        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate) || !Files.isReadable(candidate)) {
                continue;
            }
            String displayName = displayName(candidate);
            files.add(displayName);
            appendMatches(candidate, displayName, taskId, limit, matched);
        }

        return TaskLogSummaryResponse.builder()
            .taskId(taskId)
            .generatedAt(System.currentTimeMillis())
            .files(new ArrayList<>(files))
            .lines(new ArrayList<>(matched))
            .build();
    }

    int normalizeLineLimit(Integer requestedLines) {
        if (requestedLines == null) {
            return DEFAULT_LINES;
        }
        return Math.max(1, Math.min(requestedLines, MAX_LINES));
    }

    List<Path> logCandidates(String taskId) {
        List<Path> candidates = new ArrayList<>();
        Path logDir = logDir();
        for (String fileName : SHARED_LOG_FILES) {
            candidates.add(logDir.resolve(fileName).normalize());
        }
        if (isSafeTaskLogFileName(taskId)) {
            candidates.add(taskLogDir().resolve(taskId + ".log").normalize());
        }
        return candidates;
    }

    private void appendMatches(Path file, String displayName, String taskId, int limit,
                               ArrayDeque<TaskLogSummaryEntry> matched) {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                if (!line.contains(taskId)) {
                    return;
                }
                matched.add(TaskLogSummaryEntry.builder()
                    .file(displayName)
                    .line(line)
                    .build());
                while (matched.size() > limit) {
                    matched.removeFirst();
                }
            });
        } catch (IOException ignored) {
            // Ignore unreadable or concurrently rotated log files.
        }
    }

    private boolean isSafeTaskLogFileName(String taskId) {
        return !taskId.contains("/") && !taskId.contains("\\") && !taskId.contains("..");
    }

    private String displayName(Path file) {
        Path taskDir = taskLogDir();
        if (file.normalize().startsWith(taskDir)) {
            return "task/" + file.getFileName();
        }
        return file.getFileName().toString();
    }

    private Path logDir() {
        return Paths.get(System.getProperty("LOG_DIR", System.getProperty("user.dir") + "/logs")).normalize();
    }

    private Path taskLogDir() {
        return Paths.get(System.getProperty("LOG_PATH", "logs"), "task").normalize();
    }
}
