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

class ClientTaskTypeTest {

    @Test
    void testEnumValues_shouldExist() {
        
        assertThat(ClientTaskType.CONN).isNotNull();
        assertThat(ClientTaskType.PUB).isNotNull();
        assertThat(ClientTaskType.SUB).isNotNull();
        assertThat(ClientTaskType.CHAOS).isNotNull();
    }

    @Test
    void testEnumValues_shouldHaveCorrectCount() {
        
        ClientTaskType[] values = ClientTaskType.values();

        
        assertThat(values).hasSize(4);
    }

    @Test
    void testEnumValueOf_shouldReturnValue() {
        
        ClientTaskType conn = ClientTaskType.valueOf("CONN");
        ClientTaskType pub = ClientTaskType.valueOf("PUB");
        ClientTaskType sub = ClientTaskType.valueOf("SUB");
        ClientTaskType chaos = ClientTaskType.valueOf("CHAOS");

        
        assertThat(conn).isEqualTo(ClientTaskType.CONN);
        assertThat(pub).isEqualTo(ClientTaskType.PUB);
        assertThat(sub).isEqualTo(ClientTaskType.SUB);
        assertThat(chaos).isEqualTo(ClientTaskType.CHAOS);
    }

    @Test
    void testEnumEquality_sameValue_shouldBeEqual() {
        
        ClientTaskType pub1 = ClientTaskType.PUB;
        ClientTaskType pub2 = ClientTaskType.PUB;

        
        assertThat(pub1).isEqualTo(pub2);
        assertThat(pub1.hashCode()).isEqualTo(pub2.hashCode());
    }

    @Test
    void testEnumInequality_differentValue_shouldNotBeEqual() {
        
        ClientTaskType pub = ClientTaskType.PUB;
        ClientTaskType sub = ClientTaskType.SUB;

        
        assertThat(pub).isNotEqualTo(sub);
    }

    @Test
    void testEnumName_shouldReturnConstantName() {
        
        assertThat(ClientTaskType.CONN.name()).isEqualTo("CONN");
        assertThat(ClientTaskType.PUB.name()).isEqualTo("PUB");
        assertThat(ClientTaskType.SUB.name()).isEqualTo("SUB");
        assertThat(ClientTaskType.CHAOS.name()).isEqualTo("CHAOS");
    }

    @Test
    void testEnumOrdinal_shouldReturnCorrectPosition() {
        
        assertThat(ClientTaskType.CONN.ordinal()).isEqualTo(0);
        assertThat(ClientTaskType.PUB.ordinal()).isEqualTo(1);
        assertThat(ClientTaskType.SUB.ordinal()).isEqualTo(2);
        assertThat(ClientTaskType.CHAOS.ordinal()).isEqualTo(3);
    }
}
