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

package org.apache.bifromq.testsuite.certificate.model;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("tls_certificates")
public class TlsCertificate {

    @Id
    private String id;

    
    private String name;

    
    @Indexed
    private CertType type;

    
    private String certContent;

    
    private String keyContent;

    
    private LocalDateTime validFrom;

    
    private LocalDateTime validTo;

    
    private String subjectDN;

    
    private String issuerDN;

    
    @Indexed
    private String fingerprint;

    
    private LocalDateTime createdAt;

    
    private LocalDateTime updatedAt;
}