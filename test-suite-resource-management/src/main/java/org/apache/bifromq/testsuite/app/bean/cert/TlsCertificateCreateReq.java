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

package org.apache.bifromq.testsuite.app.bean.cert;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.apache.bifromq.testsuite.certificate.model.CertType;
import org.apache.commons.lang3.StringUtils;

@Data
public class TlsCertificateCreateReq {

    @NotBlank(message = "{validation.cert.name.notBlank}")
    private String name;

    @NotNull(message = "{validation.cert.type.notNull}")
    private CertType type;

    @NotBlank(message = "{validation.cert.content.notBlank}")
    private String certContent;


    private String keyContent;

    @AssertTrue(message = "{error.cert.clientNeedsPrivateKey}")
    public boolean isClientKeyContentValid() {
        return type != CertType.CLIENT || StringUtils.isNotBlank(keyContent);
    }

    @AssertTrue(message = "{error.cert.caNoPrivateKey}")
    public boolean isCaKeyContentValid() {
        return type != CertType.CA || StringUtils.isBlank(keyContent);
    }
}
