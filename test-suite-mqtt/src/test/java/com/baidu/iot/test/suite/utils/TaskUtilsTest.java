

package com.baidu.iot.test.suite.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TaskUtils.
 */
class TaskUtilsTest {

    @Test
    void testGetClientTaskAddr_shouldReturnCorrectFormat() {
        // given
        String taskId = "task-123-abc";

        // when
        String addr = TaskUtils.getClientTaskAddr(taskId);

        // then
        assertThat(addr).isEqualTo("client.task.task-123-abc");
    }

    @Test
    void testGetWorkerTaskAddr_shouldReturnCorrectFormat() {
        // given
        String taskId = "task-456-def";

        // when
        String addr = TaskUtils.getWorkerTaskAddr(taskId);

        // then
        assertThat(addr).isEqualTo("worker.task.task-456-def");
    }

    @Test
    void testGetWorkerSignalAddr_shouldReturnCorrectFormat() {
        // given
        String uniqueName = "signal-unique";

        // when
        String addr = TaskUtils.getWorkerSignalAddr(uniqueName);

        // then
        assertThat(addr).isEqualTo("worker.signal.signal-unique");
    }

    @Test
    void testGetWorkEventAddr_shouldReturnConstant() {
        // when
        String addr = TaskUtils.getWorkEventAddr();

        // then
        assertThat(addr).isEqualTo("worker.event");
    }

    @Test
    void testGetClientTaskAddr_withEmptyString_shouldReturnPrefix() {
        // given
        String taskId = "";

        // when
        String addr = TaskUtils.getClientTaskAddr(taskId);

        // then
        assertThat(addr).isEqualTo("client.task.");
    }

    @Test
    void testGetClientTaskAddr_withSpecialCharacters_shouldPreserveCharacters() {
        // given
        String taskId = "task_123.abc-def";

        // when
        String addr = TaskUtils.getClientTaskAddr(taskId);

        // then
        assertThat(addr).isEqualTo("client.task.task_123.abc-def");
    }

    @Test
    void testGetWorkerTaskAddr_consistencyWithClientTaskAddr() {
        // given
        String taskId = "same-task-id";

        // when
        String clientAddr = TaskUtils.getClientTaskAddr(taskId);
        String workerAddr = TaskUtils.getWorkerTaskAddr(taskId);

        // then
        assertThat(clientAddr).isNotEqualTo(workerAddr);
        assertThat(clientAddr).startsWith("client.task.");
        assertThat(workerAddr).startsWith("worker.task.");
        assertThat(clientAddr.substring("client.task.".length()))
                .isEqualTo(workerAddr.substring("worker.task.".length()));
    }
}
