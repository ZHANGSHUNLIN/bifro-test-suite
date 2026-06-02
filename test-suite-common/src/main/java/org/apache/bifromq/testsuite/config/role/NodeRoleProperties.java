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

package org.apache.bifromq.testsuite.config.role;

import org.apache.bifromq.testsuite.cluster.NodeRole;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class NodeRoleProperties {

    private final NodeRole nodeRole;

    public NodeRoleProperties(Environment environment) {
        String value = environment.getProperty(NodeRoleCondition.NODE_ROLE_PROPERTY);
        if (value == null || value.isBlank()) {
            value = environment.getProperty(NodeRoleCondition.NODE_ROLE_KEBAB_PROPERTY);
        }
        NodeRole parsed = NodeRole.from(value);
        this.nodeRole = parsed == NodeRole.UNKNOWN ? NodeRole.ALL : parsed;
    }

    public NodeRole getNodeRole() {
        return nodeRole;
    }
}
