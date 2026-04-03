
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
public class StatsSubResult {
    private long expectSubClientCount;
    private long actualSubClientCount;
    private long expectSubMsgCount;
    private long actualSubMsgCount;
    private double expectSubQps;
    private double actualSubQps;
    private long subMsgLoss;
    private long duplicateSubMsgCount;
    private StatsBasicResult actualResult;
}
