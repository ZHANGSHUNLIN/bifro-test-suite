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

package org.apache.bifromq.testsuite;

import org.apache.bifromq.testsuite.i18n.Messages;

import lombok.Getter;

@Getter
public enum TaskTemplate {

    

    
    CONN_STANDARD("task.template.connStandard"),

    
    CONN_IMMEDIATE_DISCONNECT("task.template.connImmediateDisconnect"),

    
    CONN_PUBLISH_ON_CONNECT("task.template.connPublishOnConnect"),

    

    
    PUBSUB_STANDARD("task.template.pubsubStandard"),

    
    PUBSUB_PUB_ONLY("task.template.pubsubPubOnly"),

    
    PUBSUB_SUB_ONLY("task.template.pubsubSubOnly"),

    

    
    CHAOS_STANDARD("task.template.chaosStandard"),

    

    
    CUSTOM("task.template.custom");

    private final String label;

    TaskTemplate(String label) {
        this.label = label;
    }

}
