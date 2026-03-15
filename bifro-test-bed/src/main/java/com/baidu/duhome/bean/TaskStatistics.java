package com.baidu.duhome.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public  class TaskStatistics {
        private Integer totalNodes;
        private Integer totalAssignedClients;
        private Integer minClientsPerNode;
        private Integer maxClientsPerNode;
        private Integer averageClientsPerNode;
        private Double distributionBalance; // 分布均衡度 (0-1)
    }