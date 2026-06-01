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

package org.apache.bifromq.testsuite.certificate.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CertParserTest {

    private final CertParser parser = new CertParser();

    @Test
    void testParsePrivateKey_nullInput_throwsException() {
        
        assertThatThrownBy(() -> parser.parsePrivateKey(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("error.cert.invalidPrivateKey");
    }

    @Test
    void testParsePrivateKey_emptyInput_throwsException() {
        
        assertThatThrownBy(() -> parser.parsePrivateKey(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("error.cert.invalidPrivateKey");
    }

    @Test
    void testParsePrivateKey_invalidInput_throwsException() {
        
        String invalidPEM = "-----BEGIN PRIVATE KEY-----\ninvalid-content!@#\n-----END PRIVATE KEY-----";

        
        assertThatThrownBy(() -> parser.parsePrivateKey(invalidPEM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("error.cert.invalidPrivateKey");
    }

    @Test
    void testParsePrivateKey_missingMarkers_throwsException() {
        
        String invalidPEM = "This is not a valid PEM format";

        
        assertThatThrownBy(() -> parser.parsePrivateKey(invalidPEM))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseCertificate_nullInput_throwsException() {
        
        assertThatThrownBy(() -> parser.parseCertificate(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("error.cert.invalidCertFormat");
    }

    @Test
    void testParseCertificate_emptyInput_throwsException() {
        
        assertThatThrownBy(() -> parser.parseCertificate(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("error.cert.invalidCertFormat");
    }

    @Test
    void testParseCertificate_missingMarkers_throwsException() {
        
        String invalidPEM = "This is not a valid certificate";

        
        assertThatThrownBy(() -> parser.parseCertificate(invalidPEM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("error.cert.invalidCertFormat");
    }
}