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

package org.apache.bifromq.testsuite.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConnectionUtilTest {

    @Test
    void testIsSSL_withSSLProtocol_shouldReturnTrue() {
        
        String protocol = ConnectionUtil.SSL_PROTOCOL;

        
        boolean result = ConnectionUtil.isSSL(protocol);

        
        assertThat(result).isTrue();
    }

    @Test
    void testIsSSL_withSSLUpperCase_shouldReturnTrue() {
        
        String protocol = "SSL";

        
        boolean result = ConnectionUtil.isSSL(protocol);

        
        assertThat(result).isTrue();
    }

    @Test
    void testIsSSL_withTCPProtocol_shouldReturnFalse() {
        
        String protocol = ConnectionUtil.TCP_PROTOCOL;

        
        boolean result = ConnectionUtil.isSSL(protocol);

        
        assertThat(result).isFalse();
    }

    @Test
    void testIsSSL_withTCPUpperCase_shouldReturnFalse() {
        
        String protocol = "TCP";

        
        boolean result = ConnectionUtil.isSSL(protocol);

        
        assertThat(result).isFalse();
    }

    @Test
    void testIsSSL_withNullProtocol_shouldReturnFalse() {
        
        String protocol = null;

        
        boolean result = ConnectionUtil.isSSL(protocol);

        
        assertThat(result).isFalse();
    }

    @Test
    void testIsSSL_withEmptyProtocol_shouldReturnFalse() {
        
        String protocol = "";

        
        boolean result = ConnectionUtil.isSSL(protocol);

        
        assertThat(result).isFalse();
    }

    @Test
    void testIsSSL_withOtherProtocol_shouldReturnFalse() {
        
        String protocol = "WS";  

        
        boolean result = ConnectionUtil.isSSL(protocol);

        
        assertThat(result).isFalse();
    }

    @Test
    void testIsSSL_withLowerCaseProtocol_shouldReturnTrue() {
        
        String protocol = "ssl";  

        
        boolean result = ConnectionUtil.isSSL(protocol);

        
        assertThat(result).isTrue();
    }

    @Test
    void testTCPProtocolConstant_shouldBeTCP() {
        
        assertThat(ConnectionUtil.TCP_PROTOCOL).isEqualTo("TCP");
    }

    @Test
    void testSSLProtocolConstant_shouldBeSSL() {
        
        assertThat(ConnectionUtil.SSL_PROTOCOL).isEqualTo("SSL");
    }
}
