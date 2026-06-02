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

package org.apache.bifromq.testsuite.app.bean.broker;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.apache.commons.lang3.RandomStringUtils;

@Data
public class MqttBrokerRequest {

    private String brokerId = RandomStringUtils.secure().next(8, true, true);

    @NotBlank(message = "{validation.broker.name.notBlank}")
    private String name;

    @NotBlank(message = "{validation.broker.address.notBlank}")
    private String host;

    @Min(value = 1, message = "{validation.port.min}")
    @Max(value = 65535, message = "{validation.port.max}")
    private int port;

    private String description;

    private String group;

    private String caCertId;

    private Boolean enabled;
}
