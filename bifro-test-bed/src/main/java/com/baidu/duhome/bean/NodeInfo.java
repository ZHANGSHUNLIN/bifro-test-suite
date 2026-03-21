package com.baidu.duhome.bean;

import com.baidu.iot.test.suite.TaskStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NodeInfo  implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String nodeName;

    private ClusterNodeInfo clusterNodeInfo;

    private Long nextPing;

    private boolean alive = true;

    private Map<String, TaskStage> taskStage;

    public boolean isAlive() {
        long l = System.currentTimeMillis();
        return l - nextPing <= 0;
    }

}
