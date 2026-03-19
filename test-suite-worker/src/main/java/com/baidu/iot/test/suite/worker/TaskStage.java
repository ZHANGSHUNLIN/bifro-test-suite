/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.worker;

/**
 * Created by mafei01 in 3/15/21 2:22 PM
 */
public enum TaskStage {

    INIT,
    START,
    INIT_PUB_CLIENT,
    INIT_PUB_CLIENTED,
    INIT_SUB_CLIENT,
    INIT_SUB_CLIENTED,
    ASSIGNED,
    ONGOING,
    COLLECTING,
    SHUTDOWN_ING,
    BREAKING,
    SHUTDOWN,
    STOPPED,
}
