
package com.baidu.iot.test.suite.utils;

/**
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
