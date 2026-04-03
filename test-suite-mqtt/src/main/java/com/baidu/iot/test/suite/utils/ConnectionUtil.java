
package com.baidu.iot.test.suite.utils;

/**
 */
public class ConnectionUtil {

    public static final String TCP_PROTOCOL = "TCP";
    public static final String SSL_PROTOCOL = "SSL";


    public static boolean isSSL(String protocol) {
        return SSL_PROTOCOL.equals(protocol);
    }

}
