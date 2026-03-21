package com.baidu.duhome.config;


import com.baidu.iot.test.suite.TaskStage;

import java.util.Arrays;
import java.util.List;

import static com.baidu.iot.test.suite.TaskStage.ASSIGNED;
import static com.baidu.iot.test.suite.TaskStage.INIT;
import static com.baidu.iot.test.suite.TaskStage.SHUTDOWN;
import static com.baidu.iot.test.suite.TaskStage.STOPPED;


public class Constants {

   public static final List<TaskStage> CAN_NOT_DEL_STATE = Arrays.asList(STOPPED, ASSIGNED, INIT, SHUTDOWN);

}
