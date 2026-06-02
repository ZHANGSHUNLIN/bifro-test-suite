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

package org.apache.bifromq.testsuite.config.node;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class NodeIdentityProperties {

    public static final String NODE_ID_MEMBER_ATTRIBUTE = "bifro.nodeId";

    private final String nodeId;

    public NodeIdentityProperties(Environment environment) {
        this.nodeId = firstNonBlank(
            environment.getProperty("bifro.node-id"),
            environment.getProperty("bifro.nodeId"),
            "local-node");
    }

    public String getNodeId() {
        return nodeId;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
