/*
 * Copyright (C) 2024 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.iot.test.suite;

public interface IPubMsgListener {

    void onPublishMessage(byte[] payload, boolean isDup);

}
