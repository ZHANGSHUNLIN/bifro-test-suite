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

package org.apache.bifromq.testsuite.constants;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConnectionStatusTest {

    @Test
    void testEnumValues_shouldExist() {
        
        assertThat(ConnectionStatus.INIT).isNotNull();
        assertThat(ConnectionStatus.CONNECTING).isNotNull();
        assertThat(ConnectionStatus.CONNECTED).isNotNull();
        assertThat(ConnectionStatus.DISCONNECTED).isNotNull();
        assertThat(ConnectionStatus.CLOSED).isNotNull();
        assertThat(ConnectionStatus.CONNECTED_FAILED).isNotNull();
    }

    @Test
    void testEnumValues_shouldHaveCorrectCount() {
        
        ConnectionStatus[] values = ConnectionStatus.values();

        
        assertThat(values).hasSize(6);
    }

    @Test
    void testEnumValueOf_shouldReturnValue() {
        
        ConnectionStatus init = ConnectionStatus.valueOf("INIT");
        ConnectionStatus connecting = ConnectionStatus.valueOf("CONNECTING");
        ConnectionStatus connected = ConnectionStatus.valueOf("CONNECTED");
        ConnectionStatus disconnected = ConnectionStatus.valueOf("DISCONNECTED");
        ConnectionStatus closed = ConnectionStatus.valueOf("CLOSED");
        ConnectionStatus connectedFailed = ConnectionStatus.valueOf("CONNECTED_FAILED");

        
        assertThat(init).isEqualTo(ConnectionStatus.INIT);
        assertThat(connecting).isEqualTo(ConnectionStatus.CONNECTING);
        assertThat(connected).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(disconnected).isEqualTo(ConnectionStatus.DISCONNECTED);
        assertThat(closed).isEqualTo(ConnectionStatus.CLOSED);
        assertThat(connectedFailed).isEqualTo(ConnectionStatus.CONNECTED_FAILED);
    }

    @Test
    void testEnumName_shouldReturnConstantName() {
        
        assertThat(ConnectionStatus.INIT.name()).isEqualTo("INIT");
        assertThat(ConnectionStatus.CONNECTING.name()).isEqualTo("CONNECTING");
        assertThat(ConnectionStatus.CONNECTED.name()).isEqualTo("CONNECTED");
        assertThat(ConnectionStatus.DISCONNECTED.name()).isEqualTo("DISCONNECTED");
        assertThat(ConnectionStatus.CLOSED.name()).isEqualTo("CLOSED");
        assertThat(ConnectionStatus.CONNECTED_FAILED.name()).isEqualTo("CONNECTED_FAILED");
    }

    @Test
    void testEnumOrdinal_shouldReturnCorrectPosition() {
        
        assertThat(ConnectionStatus.INIT.ordinal()).isEqualTo(0);
        assertThat(ConnectionStatus.CONNECTING.ordinal()).isEqualTo(1);
        assertThat(ConnectionStatus.CONNECTED.ordinal()).isEqualTo(2);
        assertThat(ConnectionStatus.DISCONNECTED.ordinal()).isEqualTo(3);
        assertThat(ConnectionStatus.CLOSED.ordinal()).isEqualTo(4);
        assertThat(ConnectionStatus.CONNECTED_FAILED.ordinal()).isEqualTo(5);
    }

    @Test
    void testEnumEquality_sameValue_shouldBeEqual() {
        
        ConnectionStatus connected1 = ConnectionStatus.CONNECTED;
        ConnectionStatus connected2 = ConnectionStatus.CONNECTED;

        
        assertThat(connected1).isEqualTo(connected2);
        assertThat(connected1.hashCode()).isEqualTo(connected2.hashCode());
    }

    @Test
    void testEnumInequality_differentValue_shouldNotBeEqual() {
        
        ConnectionStatus connected = ConnectionStatus.CONNECTED;
        ConnectionStatus disconnected = ConnectionStatus.DISCONNECTED;

        
        assertThat(connected).isNotEqualTo(disconnected);
    }
}
