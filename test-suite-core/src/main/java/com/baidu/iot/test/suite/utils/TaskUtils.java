/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.utils;

/**
 * Created by mafei01 in 3/15/21 12:06 PM
 */
public class TaskUtils {

    public static String getClientTaskAddr(String taskId) {
        return "client.task." + taskId;
    }

    public static String getWorkerTaskAddr(String taskId) {
        return "worker.task." + taskId;
    }

    public static String getWorkerSignalAddr(String uniqueName) {
        return "worker.signal." + uniqueName;
    }

    public static String getWorkEventAddr() {
        return "worker.event";
    }

}
