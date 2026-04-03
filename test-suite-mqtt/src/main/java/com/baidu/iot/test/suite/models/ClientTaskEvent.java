
package com.baidu.iot.test.suite.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import com.baidu.iot.test.suite.constants.ClientTaskType;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClientTaskEvent {

    private String taskId;
    private String clientId;
    private ClientTaskType clientTaskType;
    private EventType eventType;
    private Map<String,Object> details;

    public ClientTaskEvent putDetail(String name, Object value) {
        if (details == null) {
            details = new ConcurrentHashMap<>();
        }
        details.put(name, value);
        return this;
    }

    public ClientTaskEvent clearDetail() {
        if (details == null) {
            details = new ConcurrentHashMap<>();
        }
        details.clear();
        return this;
    }

    public enum EventType {
        CONNECT_RESULT,
        CONNECT_STATUS,
        C2S_RESULT,
        S2C_RESULT
    }

}
