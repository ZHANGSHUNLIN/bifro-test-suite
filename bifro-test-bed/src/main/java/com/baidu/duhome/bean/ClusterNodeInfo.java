package com.baidu.duhome.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClusterNodeInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    private String nodeId;
    private String host;
    private long timestamp;
    private MemoryInfo memory;
    private CpuInfo cpu;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class MemoryInfo implements Serializable{

        @Serial
        private static final long serialVersionUID = 1L;
        private long max;
        private long total;
        private long used;
        private long free;
        
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CpuInfo implements Serializable{

        @Serial
        private static final long serialVersionUID = 1L;

        private int processors;
        private double loadAverage;
        
    }
}