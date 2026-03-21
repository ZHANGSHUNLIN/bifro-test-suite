package com.baidu.iot.test.suite.worker.pojo;

import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.stats.pojo.StatsBasicResult;
import com.baidu.iot.test.suite.stats.pojo.StatsConnResult;
import com.baidu.iot.test.suite.stats.pojo.StatsPubResult;
import com.baidu.iot.test.suite.stats.pojo.StatsSubResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class EventReport {

    private TaskStage taskStage;

    private StatsBasicResult  statsBasicResult;
    private StatsSubResult  statsSubResult;
    private StatsPubResult statsPubResult;
    private StatsConnResult statsConnResult;


}
