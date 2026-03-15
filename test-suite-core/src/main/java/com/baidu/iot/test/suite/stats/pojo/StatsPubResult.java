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
public class StatsPubResult {
    private long expectPubClientCount;
    private long actualPubClientCount;
    private long expectPubMsgCount;
    private long actualPubMsgCount;
    private double expectPubQps;
    private double actualPubQps;
    private long pubFailCount;
    private StatsBasicResult actualResult;
}
