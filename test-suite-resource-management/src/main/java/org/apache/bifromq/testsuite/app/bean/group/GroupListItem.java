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

package org.apache.bifromq.testsuite.app.bean.group;

import org.apache.bifromq.testsuite.app.group.GroupManager;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupListItem {

    
    private String id;

    
    private String type;

    
    private String name;

    
    private String description;
    
    private Long count;
    
    private Instant createdAt;

    
    @JsonProperty("brokerCount")
    public Long getBrokerCount() {
        return GroupManager.TYPE_BROKER.equals(type) ? count : 0L;
    }

    @JsonProperty("taskCount")
    public Long getTaskCount() {
        return GroupManager.TYPE_TASK.equals(type) ? count : 0L;
    }

    @JsonProperty("profileCount")
    public Long getProfileCount() {
        return GroupManager.TYPE_PROFILE.equals(type) ? count : 0L;
    }
}
