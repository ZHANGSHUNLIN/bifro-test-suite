
package com.baidu.duhome.cluster.task;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public record NodeWeight(BigDecimal totalWeight, Map<String, BigDecimal> weight) {

    public BigDecimal weightPercentage(String nodeId){
        // 计算百分比：(a / b) * 100
        return weight.get(nodeId)
                .divide(totalWeight, 6, RoundingMode.HALF_UP)  // 先多保留2位，避免精度丢失
                .setScale(4, RoundingMode.HALF_UP);  // 再保留4位小数

    }

}