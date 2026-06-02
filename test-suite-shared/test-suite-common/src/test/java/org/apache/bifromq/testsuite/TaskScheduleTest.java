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

import org.junit.jupiter.api.Test;

class TaskScheduleTest {

    @Test
    void testBuilder_defaultValues() {
        TaskSchedule schedule = TaskSchedule.builder().build();

        assertThat(schedule.getOp()).isNull();
        assertThat(schedule.getId()).isNull();
    }

    @Test
    void testBuilder_withOpAndId() {
        TaskSchedule schedule = TaskSchedule.builder()
            .op(TaskSchedule.Op.REG)
            .id("task-001")
            .build();

        assertThat(schedule.getOp()).isEqualTo(TaskSchedule.Op.REG);
        assertThat(schedule.getId()).isEqualTo("task-001");
    }

    @Test
    void testSetters() {
        TaskSchedule schedule = new TaskSchedule();

        schedule.setOp(TaskSchedule.Op.UN_REG);
        schedule.setId("task-002");

        assertThat(schedule.getOp()).isEqualTo(TaskSchedule.Op.UN_REG);
        assertThat(schedule.getId()).isEqualTo("task-002");
    }

    @Test
    void testOpEnum_values() {
        TaskSchedule.Op[] ops = TaskSchedule.Op.values();

        assertThat(ops).hasSize(3);
        assertThat(ops).contains(TaskSchedule.Op.REG);
        assertThat(ops).contains(TaskSchedule.Op.UN_REG);
        assertThat(ops).contains(TaskSchedule.Op.TASK_FINISH);
    }

    @Test
    void testOpEnum_valueOf() {
        assertThat(TaskSchedule.Op.valueOf("REG")).isEqualTo(TaskSchedule.Op.REG);
        assertThat(TaskSchedule.Op.valueOf("UN_REG")).isEqualTo(TaskSchedule.Op.UN_REG);
        assertThat(TaskSchedule.Op.valueOf("TASK_FINISH")).isEqualTo(TaskSchedule.Op.TASK_FINISH);
    }

    @Test
    void testEquals() {
        TaskSchedule schedule1 = TaskSchedule.builder()
            .op(TaskSchedule.Op.REG)
            .id("task-001")
            .build();

        TaskSchedule schedule2 = TaskSchedule.builder()
            .op(TaskSchedule.Op.REG)
            .id("task-001")
            .build();

        assertThat(schedule1).isEqualTo(schedule2);
    }

    @Test
    void testHashCode() {
        TaskSchedule schedule1 = TaskSchedule.builder()
            .op(TaskSchedule.Op.REG)
            .id("task-001")
            .build();

        TaskSchedule schedule2 = TaskSchedule.builder()
            .op(TaskSchedule.Op.REG)
            .id("task-001")
            .build();

        assertThat(schedule1.hashCode()).isEqualTo(schedule2.hashCode());
    }

    @Test
    void testToString() {
        TaskSchedule schedule = TaskSchedule.builder()
            .op(TaskSchedule.Op.TASK_FINISH)
            .id("task-001")
            .build();

        String str = schedule.toString();

        assertThat(str).contains("TASK_FINISH");
        assertThat(str).contains("task-001");
    }
}
