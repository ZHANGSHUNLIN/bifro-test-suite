
package com.baidu.duhome.config.vertx.codec;

import com.baidu.duhome.bean.ClusterNodeInfo;
import com.baidu.duhome.bean.NodeInfo;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.pojo.MqttBroker;
import com.baidu.iot.test.suite.TaskSchedule;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.WillConfig;
import com.baidu.iot.test.suite.models.ClientTaskEvent;
import com.baidu.iot.test.suite.worker.TaskConfig;
import com.baidu.iot.test.suite.worker.models.WorkerTaskEvent;
import com.hazelcast.nio.serialization.StreamSerializer;
import io.vertx.core.eventbus.MessageCodec;

public enum CodecType {


    WorkerTaskEvent(new GenericCodecSupplier<>(1000, WorkerTaskEvent.class)),
    TaskConfig(new GenericCodecSupplier<>(1001, TaskConfig.class)),
    WillConfig(new GenericCodecSupplier<>(1002, WillConfig.class)),
    ClientTaskEvent(new GenericCodecSupplier<>(1003, ClientTaskEvent.class)),
    TaskSchedule(new GenericCodecSupplier<>(1004, TaskSchedule.class)),
    MqttBroker(new GenericCodecSupplier<>(1005, MqttBroker.class)),
    TaskInfoMetadata(new GenericCodecSupplier<>(1006, TaskInfoMetadata.class)),

    ClusterNodeInfo(new GenericCodecSupplier<>(1007, ClusterNodeInfo.class)),
    NodeInfo(new GenericCodecSupplier<>(1008, NodeInfo.class)),
    MemoryInfo(new GenericCodecSupplier<>(1009, ClusterNodeInfo.MemoryInfo.class)),
    CpuInfo(new GenericCodecSupplier<>(1010, ClusterNodeInfo.CpuInfo.class)),
    TaskStage(new GenericCodecSupplier<>(1011, TaskStage.class)),



    ;
    private final CodecSupplier<?> codecSupplier;

    <T> CodecType(CodecSupplier<T> codecSupplier) {
        this.codecSupplier = codecSupplier;
    }

    @SuppressWarnings("unchecked")
    public <T> Class<T> getMessageClass() {
        return (Class<T>) codecSupplier.messageClass();
    }

    @SuppressWarnings("unchecked")
    public <T> MessageCodec<T, T> getCodec() {
        return (MessageCodec<T, T>) codecSupplier.get();
    }

    public <T> StreamSerializer<T> getSerializer() {
        return (StreamSerializer<T>) codecSupplier.getSerializer();
    }
}
    