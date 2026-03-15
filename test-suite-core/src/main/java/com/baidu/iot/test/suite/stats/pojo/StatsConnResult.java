/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.stats.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by mafei01 in 3/12/21 3:39 PM
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
