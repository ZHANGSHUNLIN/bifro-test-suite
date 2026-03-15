package com.baidu.iot.test.suite;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WillConfig implements java.io.Serializable {

    /**
     * 是否启用遗嘱消息
     */
    private Boolean willFlag = false;

    private String willTopic;
    /**
     * 遗嘱消息内容 和 willMessageLen 二选一； 优先willMessage
     */
    private String willMessage;

    /**
     * 遗嘱消息长度,在不关心遗嘱消息内容时可以指定遗嘱消息长度, 遗嘱消息长度大于0且遗嘱消息内容为空时，服务器会使用遗嘱消息长度作为遗嘱消息内容的长度
     */
    private Integer willMessageLen;

    private Integer willQos;

    private Boolean willRetain;



}
