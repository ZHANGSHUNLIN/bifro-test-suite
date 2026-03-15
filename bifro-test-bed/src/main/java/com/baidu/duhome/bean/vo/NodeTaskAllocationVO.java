package com.baidu.duhome.bean.vo;

import lombok.Data;

import java.util.List;

@Data
public class NodeTaskAllocationVO {

    private Integer totalClientCount;

    private List<NodeAllocation> nodeAllocationList;

    @Data
    public static class NodeAllocation {
        private String nodeId;
        private Integer allocatedClientCount;
    }

}


