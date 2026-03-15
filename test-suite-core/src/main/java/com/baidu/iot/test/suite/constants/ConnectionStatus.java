/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.constants;

/**
 * Created by mafei01 in 3/10/21 2:37 PM
 */
public enum ConnectionStatus {

    INIT,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    CLOSED,            // client closed and lifecycle end
    CONNECTED_FAILED   // failed after max reconnect attempts
}
