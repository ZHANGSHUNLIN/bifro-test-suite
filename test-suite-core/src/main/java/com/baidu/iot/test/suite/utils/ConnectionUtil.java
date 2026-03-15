/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.utils;

/**
 * Created by mafei01 in 3/10/21 11:43 AM
 */
public class ConnectionUtil {

    public static final String TCP_PROTOCOL = "TCP";
    public static final String SSL_PROTOCOL = "SSL";


    public static boolean isSSL(String protocol) {
        return SSL_PROTOCOL.equals(protocol);
    }

}
