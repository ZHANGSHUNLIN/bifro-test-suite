package com.baidu.duhome.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeTaskAllocationVO {

    private Integer totalClientCount;

    private List<NodeAllocation> nodeAllocationList;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NodeAllocation {
        private String nodeId;
        private Integer allocatedClientCount;
    }

}
