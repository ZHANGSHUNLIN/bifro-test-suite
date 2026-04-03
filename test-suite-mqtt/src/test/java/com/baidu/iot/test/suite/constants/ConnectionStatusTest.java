

package com.baidu.iot.test.suite.constants;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ConnectionStatus enum.
 */
class ConnectionStatusTest {

    @Test
    void testEnumValues_shouldExist() {
        // then
        assertThat(ConnectionStatus.INIT).isNotNull();
        assertThat(ConnectionStatus.CONNECTING).isNotNull();
        assertThat(ConnectionStatus.CONNECTED).isNotNull();
        assertThat(ConnectionStatus.DISCONNECTED).isNotNull();
        assertThat(ConnectionStatus.CLOSED).isNotNull();
        assertThat(ConnectionStatus.CONNECTED_FAILED).isNotNull();
    }

    @Test
    void testEnumValues_shouldHaveCorrectCount() {
        // when
        ConnectionStatus[] values = ConnectionStatus.values();

        // then
        assertThat(values).hasSize(6);
    }

    @Test
    void testEnumValueOf_shouldReturnValue() {
        // when
        ConnectionStatus init = ConnectionStatus.valueOf("INIT");
        ConnectionStatus connecting = ConnectionStatus.valueOf("CONNECTING");
        ConnectionStatus connected = ConnectionStatus.valueOf("CONNECTED");
        ConnectionStatus disconnected = ConnectionStatus.valueOf("DISCONNECTED");
        ConnectionStatus closed = ConnectionStatus.valueOf("CLOSED");
        ConnectionStatus connectedFailed = ConnectionStatus.valueOf("CONNECTED_FAILED");

        // then
        assertThat(init).isEqualTo(ConnectionStatus.INIT);
        assertThat(connecting).isEqualTo(ConnectionStatus.CONNECTING);
        assertThat(connected).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(disconnected).isEqualTo(ConnectionStatus.DISCONNECTED);
        assertThat(closed).isEqualTo(ConnectionStatus.CLOSED);
        assertThat(connectedFailed).isEqualTo(ConnectionStatus.CONNECTED_FAILED);
    }

    @Test
    void testEnumName_shouldReturnConstantName() {
        // then
        assertThat(ConnectionStatus.INIT.name()).isEqualTo("INIT");
        assertThat(ConnectionStatus.CONNECTING.name()).isEqualTo("CONNECTING");
        assertThat(ConnectionStatus.CONNECTED.name()).isEqualTo("CONNECTED");
        assertThat(ConnectionStatus.DISCONNECTED.name()).isEqualTo("DISCONNECTED");
        assertThat(ConnectionStatus.CLOSED.name()).isEqualTo("CLOSED");
        assertThat(ConnectionStatus.CONNECTED_FAILED.name()).isEqualTo("CONNECTED_FAILED");
    }

    @Test
    void testEnumOrdinal_shouldReturnCorrectPosition() {
        // then
        assertThat(ConnectionStatus.INIT.ordinal()).isEqualTo(0);
        assertThat(ConnectionStatus.CONNECTING.ordinal()).isEqualTo(1);
        assertThat(ConnectionStatus.CONNECTED.ordinal()).isEqualTo(2);
        assertThat(ConnectionStatus.DISCONNECTED.ordinal()).isEqualTo(3);
        assertThat(ConnectionStatus.CLOSED.ordinal()).isEqualTo(4);
        assertThat(ConnectionStatus.CONNECTED_FAILED.ordinal()).isEqualTo(5);
    }

    @Test
    void testEnumEquality_sameValue_shouldBeEqual() {
        // given
        ConnectionStatus connected1 = ConnectionStatus.CONNECTED;
        ConnectionStatus connected2 = ConnectionStatus.CONNECTED;

        // then
        assertThat(connected1).isEqualTo(connected2);
        assertThat(connected1.hashCode()).isEqualTo(connected2.hashCode());
    }

    @Test
    void testEnumInequality_differentValue_shouldNotBeEqual() {
        // given
        ConnectionStatus connected = ConnectionStatus.CONNECTED;
        ConnectionStatus disconnected = ConnectionStatus.DISCONNECTED;

        // then
        assertThat(connected).isNotEqualTo(disconnected);
    }
}
