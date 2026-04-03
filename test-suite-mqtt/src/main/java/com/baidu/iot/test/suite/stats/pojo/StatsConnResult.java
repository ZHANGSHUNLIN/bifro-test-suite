
package com.baidu.iot.test.suite.stats.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsConnResult {
    private long expectConnCount;
    private long actualConnCount;
    private double expectConnQps;
    private double actualConnQps;
    private long connectFailCount;
    private StatsBasicResult actualResult;
}
