package com.baidu.iot.test.suite;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TaskStage} enum
 */
class TaskStageTest {

    @Test
    void testTaskStage_shouldHaveAllRequiredStages() {
        // Then
        assertThat(TaskStage.values()).isNotNull();
        assertThat(TaskStage.values()).isNotEmpty();
        assertThat(TaskStage.values()).hasSize(16);

        // Verify all required stages exist
        assertThat(TaskStage.INIT).isNotNull();
        assertThat(TaskStage.ASSIGNED).isNotNull();
        assertThat(TaskStage.START).isNotNull();
        assertThat(TaskStage.INIT_CLIENT).isNotNull();
        assertThat(TaskStage.INIT_PUB_CLIENT).isNotNull();
        assertThat(TaskStage.INIT_SUB_CLIENT).isNotNull();
        assertThat(TaskStage.INIT_KAFKA_CLIENT).isNotNull();
        assertThat(TaskStage.PRODUCING).isNotNull();
        assertThat(TaskStage.DATABASE_CONNECTING).isNotNull();
        assertThat(TaskStage.DATABASE_OPERATING).isNotNull();
        assertThat(TaskStage.ONGOING).isNotNull();
        assertThat(TaskStage.SHUTTING).isNotNull();
        assertThat(TaskStage.SHUTDOWN).isNotNull();
        assertThat(TaskStage.STOPPED).isNotNull();
        assertThat(TaskStage.FAILED).isNotNull();
        assertThat(TaskStage.TIMEOUT).isNotNull();
    }

    @Test
    void testTaskStage_values_shouldIncludeAllStages() {
        // Given & Then
        TaskStage[] stages = TaskStage.values();
        assertThat(stages).containsExactlyInAnyOrder(
                TaskStage.INIT,
                TaskStage.ASSIGNED,
                TaskStage.START,
                TaskStage.INIT_CLIENT,
                TaskStage.INIT_PUB_CLIENT,
                TaskStage.INIT_SUB_CLIENT,
                TaskStage.INIT_KAFKA_CLIENT,
                TaskStage.PRODUCING,
                TaskStage.DATABASE_CONNECTING,
                TaskStage.DATABASE_OPERATING,
                TaskStage.ONGOING,
                TaskStage.SHUTTING,
                TaskStage.SHUTDOWN,
                TaskStage.STOPPED,
                TaskStage.FAILED,
                TaskStage.TIMEOUT
        );
    }

    @Test
    void testTaskStage_name_shouldReturnCorrectName() {
        // Given & Then
        assertThat(TaskStage.INIT.name()).isEqualTo("INIT");
        assertThat(TaskStage.START.name()).isEqualTo("START");
        assertThat(TaskStage.ONGOING.name()).isEqualTo("ONGOING");
        assertThat(TaskStage.SHUTDOWN.name()).isEqualTo("SHUTDOWN");
        assertThat(TaskStage.FAILED.name()).isEqualTo("FAILED");
        assertThat(TaskStage.TIMEOUT.name()).isEqualTo("TIMEOUT");
    }

    @Test
    void testTaskStage_ordinal_shouldBeSequential() {
        // Given & Then
        assertThat(TaskStage.INIT.ordinal()).isEqualTo(0);
        assertThat(TaskStage.ASSIGNED.ordinal()).isEqualTo(1);
        assertThat(TaskStage.START.ordinal()).isEqualTo(2);
        assertThat(TaskStage.INIT_CLIENT.ordinal()).isEqualTo(3);
        assertThat(TaskStage.INIT_PUB_CLIENT.ordinal()).isEqualTo(4);
        assertThat(TaskStage.INIT_SUB_CLIENT.ordinal()).isEqualTo(5);
        assertThat(TaskStage.ONGOING.ordinal()).isEqualTo(10);
        assertThat(TaskStage.SHUTDOWN.ordinal()).isEqualTo(12);
        assertThat(TaskStage.FAILED.ordinal()).isEqualTo(14);
    }

    @Test
    void testTaskStage_valueOf_shouldReturnCorrectStage() {
        // Given & Then
        assertThat(TaskStage.valueOf("INIT")).isEqualTo(TaskStage.INIT);
        assertThat(TaskStage.valueOf("ONGOING")).isEqualTo(TaskStage.ONGOING);
        assertThat(TaskStage.valueOf("SHUTDOWN")).isEqualTo(TaskStage.SHUTDOWN);
    }
}
