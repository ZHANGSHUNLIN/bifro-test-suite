
package com.baidu.iot.test.suite.constants;

/**
 */
public enum ConnectionStatus {

    INIT,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    CLOSED,            // client closed and lifecycle end
    CONNECTED_FAILED   // failed after max reconnect attempts
}
