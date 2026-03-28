/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.iot.test.suite.constants;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ClientTaskType enum.
 */
class ClientTaskTypeTest {

    @Test
    void testEnumValues_shouldExist() {
        // then
        assertThat(ClientTaskType.CONN).isNotNull();
        assertThat(ClientTaskType.PUB).isNotNull();
        assertThat(ClientTaskType.SUB).isNotNull();
    }

    @Test
    void testEnumValues_shouldHaveCorrectCount() {
        // when
        ClientTaskType[] values = ClientTaskType.values();

        // then
        assertThat(values).hasSize(3);
    }

    @Test
    void testEnumValueOf_shouldReturnValue() {
        // when
        ClientTaskType conn = ClientTaskType.valueOf("CONN");
        ClientTaskType pub = ClientTaskType.valueOf("PUB");
        ClientTaskType sub = ClientTaskType.valueOf("SUB");

        // then
        assertThat(conn).isEqualTo(ClientTaskType.CONN);
        assertThat(pub).isEqualTo(ClientTaskType.PUB);
        assertThat(sub).isEqualTo(ClientTaskType.SUB);
    }

    @Test
    void testEnumEquality_sameValue_shouldBeEqual() {
        // given
        ClientTaskType pub1 = ClientTaskType.PUB;
        ClientTaskType pub2 = ClientTaskType.PUB;

        // then
        assertThat(pub1).isEqualTo(pub2);
        assertThat(pub1.hashCode()).isEqualTo(pub2.hashCode());
    }

    @Test
    void testEnumInequality_differentValue_shouldNotBeEqual() {
        // given
        ClientTaskType pub = ClientTaskType.PUB;
        ClientTaskType sub = ClientTaskType.SUB;

        // then
        assertThat(pub).isNotEqualTo(sub);
    }

    @Test
    void testEnumName_shouldReturnConstantName() {
        // then
        assertThat(ClientTaskType.CONN.name()).isEqualTo("CONN");
        assertThat(ClientTaskType.PUB.name()).isEqualTo("PUB");
        assertThat(ClientTaskType.SUB.name()).isEqualTo("SUB");
    }

    @Test
    void testEnumOrdinal_shouldReturnCorrectPosition() {
        // then
        assertThat(ClientTaskType.CONN.ordinal()).isEqualTo(0);
        assertThat(ClientTaskType.PUB.ordinal()).isEqualTo(1);
        assertThat(ClientTaskType.SUB.ordinal()).isEqualTo(2);
    }
}
