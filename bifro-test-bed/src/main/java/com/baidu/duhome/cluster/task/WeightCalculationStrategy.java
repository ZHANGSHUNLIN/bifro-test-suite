package com.baidu.duhome.cluster.task;

import com.baidu.duhome.bean.ClusterNodeInfo;
import com.baidu.duhome.bean.NodeInfo;

import java.util.Map;

public interface WeightCalculationStrategy {

    NodeWeight calculateWeights(Map<String, NodeInfo> nodeInfos, int cpuWeight, int memWeight);

}