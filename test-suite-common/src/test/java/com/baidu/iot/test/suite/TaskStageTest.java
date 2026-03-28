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
        assertThat(TaskStage.values()).hasSize(13);

        // Verify all required stages exist
        assertThat(TaskStage.INIT).isNotNull();
        assertThat(TaskStage.START).isNotNull();
        assertThat(TaskStage.INIT_PUB_CLIENT).isNotNull();
        assertThat(TaskStage.INIT_PUB_CLIENTED).isNotNull();
        assertThat(TaskStage.INIT_SUB_CLIENT).isNotNull();
        assertThat(TaskStage.INIT_SUB_CLIENTED).isNotNull();
        assertThat(TaskStage.ASSIGNED).isNotNull();
        assertThat(TaskStage.ONGOING).isNotNull();
        assertThat(TaskStage.COLLECTING).isNotNull();
        assertThat(TaskStage.SHUTDOWN_ING).isNotNull();
        assertThat(TaskStage.BREAKING).isNotNull();
        assertThat(TaskStage.SHUTDOWN).isNotNull();
        assertThat(TaskStage.STOPPED).isNotNull();
    }

    @Test
    void testTaskStage_values_shouldIncludeAllStages() {
        // Given & Then
        TaskStage[] stages = TaskStage.values();
        assertThat(stages).containsExactlyInAnyOrder(
                TaskStage.INIT,
                TaskStage.START,
                TaskStage.INIT_PUB_CLIENT,
                TaskStage.INIT_PUB_CLIENTED,
                TaskStage.INIT_SUB_CLIENT,
                TaskStage.INIT_SUB_CLIENTED,
                TaskStage.ASSIGNED,
                TaskStage.ONGOING,
                TaskStage.COLLECTING,
                TaskStage.SHUTDOWN_ING,
                TaskStage.BREAKING,
                TaskStage.SHUTDOWN,
                TaskStage.STOPPED
        );
    }

    @Test
    void testTaskStage_name_shouldReturnCorrectName() {
        // Given & Then
        assertThat(TaskStage.INIT.name()).isEqualTo("INIT");
        assertThat(TaskStage.START.name()).isEqualTo("START");
        assertThat(TaskStage.ONGOING.name()).isEqualTo("ONGOING");
        assertThat(TaskStage.SHUTDOWN.name()).isEqualTo("SHUTDOWN");
    }

    @Test
    void testTaskStage_ordinal_shouldBeSequential() {
        // Given & Then
        assertThat(TaskStage.INIT.ordinal()).isEqualTo(0);
        assertThat(TaskStage.START.ordinal()).isEqualTo(1);
        assertThat(TaskStage.ONGOING.ordinal()).isEqualTo(7);
        assertThat(TaskStage.SHUTDOWN.ordinal()).isEqualTo(11);
    }

    @Test
    void testTaskStage_valueOf_shouldReturnCorrectStage() {
        // Given & Then
        assertThat(TaskStage.valueOf("INIT")).isEqualTo(TaskStage.INIT);
        assertThat(TaskStage.valueOf("ONGOING")).isEqualTo(TaskStage.ONGOING);
        assertThat(TaskStage.valueOf("SHUTDOWN")).isEqualTo(TaskStage.SHUTDOWN);
    }
}
