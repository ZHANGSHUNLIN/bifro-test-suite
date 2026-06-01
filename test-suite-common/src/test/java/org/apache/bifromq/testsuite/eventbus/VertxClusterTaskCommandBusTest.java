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

package org.apache.bifromq.testsuite.eventbus;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageCodec;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.bifromq.testsuite.TaskSchedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VertxClusterTaskCommandBusTest {

    private Vertx vertx;
    private VertxClusterTaskCommandBus commandBus;
    private LinkedBlockingQueue<TaskSchedule> messages;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        vertx.eventBus().registerDefaultCodec(TaskSchedule.class, new LocalTaskScheduleCodec());
        commandBus = new VertxClusterTaskCommandBus(vertx.eventBus());
        messages = new LinkedBlockingQueue<>();
        vertx.eventBus().<TaskSchedule>consumer(EventBusAddresses.CLUSTER_TASK_COMMAND,
            message -> messages.offer(message.body()));
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    void broadcastStart_shouldPublishRegSchedule() throws Exception {
        commandBus.broadcastStart("task-a");

        TaskSchedule schedule = messages.poll(2, TimeUnit.SECONDS);
        assertThat(schedule).isNotNull();
        assertThat(schedule.getOp()).isEqualTo(TaskSchedule.Op.REG);
        assertThat(schedule.getId()).isEqualTo("task-a");
        assertThat(schedule.getMessageId()).isNotBlank();
    }

    @Test
    void broadcastTaskFinished_shouldPublishSourceNode() throws Exception {
        commandBus.broadcastTaskFinished("task-a", "node-a");

        TaskSchedule schedule = messages.poll(2, TimeUnit.SECONDS);
        assertThat(schedule).isNotNull();
        assertThat(schedule.getOp()).isEqualTo(TaskSchedule.Op.TASK_FINISH);
        assertThat(schedule.getSourceNodeId()).isEqualTo("node-a");
    }

    private static class LocalTaskScheduleCodec implements MessageCodec<TaskSchedule, TaskSchedule> {
        @Override
        public void encodeToWire(Buffer buffer, TaskSchedule taskSchedule) {
            throw new UnsupportedOperationException("Local codec only");
        }

        @Override
        public TaskSchedule decodeFromWire(int pos, Buffer buffer) {
            throw new UnsupportedOperationException("Local codec only");
        }

        @Override
        public TaskSchedule transform(TaskSchedule taskSchedule) {
            return taskSchedule;
        }

        @Override
        public String name() {
            return "local-task-schedule";
        }

        @Override
        public byte systemCodecID() {
            return -1;
        }
    }
}
