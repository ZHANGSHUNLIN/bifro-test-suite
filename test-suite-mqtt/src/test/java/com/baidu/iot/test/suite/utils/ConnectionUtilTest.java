

package com.baidu.iot.test.suite.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ConnectionUtil.
 */
class ConnectionUtilTest {

    @Test
    void testIsSSL_withSSLProtocol_shouldReturnTrue() {
        // given
        String protocol = ConnectionUtil.SSL_PROTOCOL;

        // when
        boolean result = ConnectionUtil.isSSL(protocol);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void testIsSSL_withSSLUpperCase_shouldReturnTrue() {
        // given
        String protocol = "SSL";

        // when
        boolean result = ConnectionUtil.isSSL(protocol);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void testIsSSL_withTCPProtocol_shouldReturnFalse() {
        // given
        String protocol = ConnectionUtil.TCP_PROTOCOL;

        // when
        boolean result = ConnectionUtil.isSSL(protocol);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void testIsSSL_withTCPUpperCase_shouldReturnFalse() {
        // given
        String protocol = "TCP";

        // when
        boolean result = ConnectionUtil.isSSL(protocol);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void testIsSSL_withNullProtocol_shouldReturnFalse() {
        // given
        String protocol = null;

        // when
        boolean result = ConnectionUtil.isSSL(protocol);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void testIsSSL_withEmptyProtocol_shouldReturnFalse() {
        // given
        String protocol = "";

        // when
        boolean result = ConnectionUtil.isSSL(protocol);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void testIsSSL_withOtherProtocol_shouldReturnFalse() {
        // given
        String protocol = "WS";  // WebSocket

        // when
        boolean result = ConnectionUtil.isSSL(protocol);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void testIsSSL_withMixedCaseProtocol_shouldReturnFalse() {
        // given
        String protocol = "ssl";  // lowercase

        // when
        boolean result = ConnectionUtil.isSSL(protocol);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void testTCPProtocolConstant_shouldBeTCP() {
        // then
        assertThat(ConnectionUtil.TCP_PROTOCOL).isEqualTo("TCP");
    }

    @Test
    void testSSLProtocolConstant_shouldBeSSL() {
        // then
        assertThat(ConnectionUtil.SSL_PROTOCOL).isEqualTo("SSL");
    }
}
