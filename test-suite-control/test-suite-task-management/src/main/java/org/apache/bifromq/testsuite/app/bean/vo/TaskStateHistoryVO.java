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

package org.apache.bifromq.testsuite.app.bean.vo;

import org.apache.bifromq.testsuite.app.database.pojo.TaskStateHistory;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskStateHistoryVO {

    
    private String fromStage;

    
    private String toStage;

    
    private String triggerEvent;

    
    private Long timestamp;

    
    private String nodeId;

    
    private String nodeName;

    private String errorMessage;

    private String source;

    private Map<String, Object> metadata;

    public static TaskStateHistoryVO from(TaskStateHistory history) {
        TaskStateHistoryVO vo = new TaskStateHistoryVO();
        vo.setFromStage(history.getFromStage() != null ? history.getFromStage().name() : null);
        vo.setToStage(history.getToStage() != null ? history.getToStage().name() : null);
        vo.setTriggerEvent(history.getTriggerEvent() != null ? history.getTriggerEvent().name() : null);
        vo.setTimestamp(history.getTimestamp() != null ? history.getTimestamp().toEpochMilli() : null);
        vo.setNodeId(history.getNodeId());
        vo.setNodeName(history.getNodeName());
        vo.setErrorMessage(history.getErrorMessage());
        vo.setSource(history.getSource());
        vo.setMetadata(history.getMetadata());
        return vo;
    }
}
