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

package org.apache.bifromq.testsuite.worker.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.pipeline.PipelineStageSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TaskLogger {

    private static final Logger PIPELINE_LOGGER = LoggerFactory.getLogger("TASK_PIPELINE");
    private static final Logger STATE_MACHINE_LOGGER = LoggerFactory.getLogger("TASK_STATE_MACHINE");

    private static final String LOG_DIR = System.getProperty("LOG_PATH", "logs") + "/task";

    private static final Map<String, Path> TASK_LOG_FILES = new ConcurrentHashMap<>();

    private TaskLogger() {
    }

    public static void initTaskLog(String taskId) {
        try {
            Path logDir = Paths.get(LOG_DIR);
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }

            Path logFile = logDir.resolve(taskId + ".log");
            if (!Files.exists(logFile)) {
                Files.createFile(logFile);
            }

            TASK_LOG_FILES.put(taskId, logFile);

            String timestamp = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            writeLog(taskId, String.format("[%s] ========== Task Log Started ==========%n", timestamp));

        } catch (IOException e) {
            System.err.println("Failed to init task log file: " + taskId + ", error: " + e.getMessage());
        }
    }

    public static void closeTaskLog(String taskId) {
        Path path = TASK_LOG_FILES.remove(taskId);
        if (path != null) {
            String timestamp = Instant.now().toString();
            writeLog(taskId, String.format("[%s] ========== Task Log Closed ==========%n%n", timestamp));
        }
    }

    public static void logStateTransition(String taskId, String nodeId,
                                          TaskStage from, TaskStage to,
                                          TaskEvent event, Instant timestamp) {
        String message = String.format("[%s] [STATE] [nodeId=%s] %s -> %s (event=%s)%n",
            formatTime(timestamp),
            nodeId != null ? nodeId : "MAIN",
            from, to, event);

        writeLog(taskId, message);

        STATE_MACHINE_LOGGER.info("[taskId={}] [nodeId={}] {} -> {} (event={})",
            taskId, nodeId != null ? nodeId : "MAIN", from, to, event);
    }

    public static void logTaskStart(String taskId, String taskType, int clientCount) {
        initTaskLog(taskId);

        String message = String.format("[%s] [TASK] Task started: type=%s, clients=%d%n",
            formatTime(Instant.now()), taskType, clientCount);
        writeLog(taskId, message);

        PIPELINE_LOGGER.info("[taskId={}] Task started: type={}, clients={}", taskId, taskType, clientCount);
    }

    public static void logTaskStop(String taskId, TaskStage currentStage) {
        String message = String.format("[%s] [TASK] Task stopping at stage: %s%n",
            formatTime(Instant.now()), currentStage);
        writeLog(taskId, message);

        closeTaskLog(taskId);

        PIPELINE_LOGGER.info("[taskId={}] Task stopped at stage: {}", taskId, currentStage);
    }

    public static void logStageEvent(String taskId, String nodeId, PipelineStageSnapshot snapshot,
                                     int totalClients, String clientTag) {
        if (snapshot == null) {
            return;
        }
        String event = stageEvent(snapshot.getStatus());
        String line = keyValueLine(
            "event", event,
            "taskId", safe(taskId),
            "nodeId", safe(nodeId),
            "stage", safe(snapshot.getKey()),
            "status", safe(snapshot.getStatus()),
            "clientTag", safe(clientTag),
            "total", totalClients,
            "started", snapshot.getStarted(),
            "completed", snapshot.getCompleted(),
            "success", successCount(snapshot),
            "failed", snapshot.getFailed(),
            "pending", snapshot.getPending(),
            "durationMs", snapshot.getDurationMs(),
            "reasonSummary", summarize(snapshot.getFailureReasons()),
            "pendingSamples", summarizeList(snapshot.getPendingSamples()),
            "failureReason", safe(snapshot.getFailureReason()));

        writeLog(taskId, String.format("[%s] [PIPELINE] %s%n", formatTime(Instant.now()), line));
        PIPELINE_LOGGER.info(line);
    }

    private static void writeLog(String taskId, String message) {
        Path logFile = TASK_LOG_FILES.get(taskId);
        if (logFile == null) {
            logFile = Paths.get(LOG_DIR, taskId + ".log");
            TASK_LOG_FILES.put(taskId, logFile);
        }

        try {
            Path parentDir = logFile.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            Files.writeString(logFile, message,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write task log: " + taskId + ", error: " + e.getMessage());
        }
    }

    private static String formatTime(Instant instant) {
        return instant != null ? instant.toString() : Instant.now().toString();
    }

    private static String stageEvent(String status) {
        if (status == null) {
            return "stage-update";
        }
        return switch (status) {
            case "RUNNING" -> "stage-start";
            case "DONE" -> "stage-end";
            case "FAILED" -> "stage-error";
            case "CANCELLED" -> "stage-cancel";
            default -> "stage-update";
        };
    }

    private static Integer successCount(PipelineStageSnapshot snapshot) {
        Integer completed = snapshot.getCompleted();
        Integer failed = snapshot.getFailed();
        if (completed == null) {
            return null;
        }
        return Math.max(0, completed - (failed == null ? 0 : failed));
    }

    private static String keyValueLine(Object... keyValues) {
        StringJoiner joiner = new StringJoiner(" ");
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            joiner.add(keyValues[i] + "=" + safeValue(keyValues[i + 1]));
        }
        return joiner.toString();
    }

    private static String safeValue(Object value) {
        if (value == null) {
            return "-";
        }
        return String.valueOf(value)
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(' ', '_');
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String summarize(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        StringJoiner joiner = new StringJoiner(",");
        values.forEach((key, value) -> joiner.add(safeValue(key) + ":" + value));
        return joiner.toString();
    }

    private static String summarizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        StringJoiner joiner = new StringJoiner(",");
        values.forEach(value -> joiner.add(safeValue(value)));
        return joiner.toString();
    }
}
