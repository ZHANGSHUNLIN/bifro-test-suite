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

import java.util.Arrays;
import java.util.Map;
import org.apache.bifromq.testsuite.cluster.NodeRole;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class NodeRoleCondition implements Condition {

    public static final String NODE_ROLE_PROPERTY = "bifro.nodeRole";
    public static final String NODE_ROLE_KEBAB_PROPERTY = "bifro.node-role";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnNodeRole.class.getName());
        NodeRole currentRole = currentRole(context);
        if (attributes == null) {
            if (metadata.isAnnotated(ConditionalOnControlPlane.class.getName())) {
                return currentRole == NodeRole.CONTROL || currentRole == NodeRole.ALL;
            }
            if (metadata.isAnnotated(ConditionalOnWorkerPlane.class.getName())) {
                return currentRole == NodeRole.WORKER || currentRole == NodeRole.ALL;
            }
            return true;
        }
        NodeRole[] acceptedRoles = (NodeRole[]) attributes.get("value");
        return Arrays.asList(acceptedRoles).contains(currentRole);
    }

    private NodeRole currentRole(ConditionContext context) {
        String value = context.getEnvironment().getProperty(NODE_ROLE_PROPERTY);
        if (value == null || value.isBlank()) {
            value = context.getEnvironment().getProperty(NODE_ROLE_KEBAB_PROPERTY);
        }
        if (value == null || value.isBlank()) {
            return NodeRole.ALL;
        }
        return NodeRole.from(value);
    }
}
