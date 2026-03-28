package com.baidu.iot.test.suite.worker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskBroker implements Serializable {

    private static final long serialVersionUID = 1L;

    private String host;

    private int port;

}
