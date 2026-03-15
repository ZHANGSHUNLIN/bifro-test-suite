package com.baidu.iot.test.suite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskSchedule {

    private Op op;

    private String id;

    public enum Op {

        REG,
        UN_REG,
        /**
         * 任务完成后会publish TaskSchedule{op=TASK_FINISH, taskId=xxx}事件。 集群数据管理器会监听这个事件针对所有的任务进行过滤
         */
        TASK_FINISH
    }

}
