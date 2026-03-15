
package com.baidu.duhome.config.vertx.codec;

import com.baidu.duhome.bean.ClusterNodeInfo;
import com.baidu.duhome.bean.NodeInfo;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.pojo.MqttBroker;
import com.baidu.iot.test.suite.TaskSchedule;
import com.baidu.iot.test.suite.WillConfig;
import com.baidu.iot.test.suite.models.ClientTaskEvent;
import com.baidu.iot.test.suite.worker.TaskConfig;
import com.baidu.iot.test.suite.worker.models.WorkerTaskEvent;
import io.vertx.core.eventbus.MessageCodec;

    public enum CodecType {
        


        WorkerTaskEvent(new GenericCodecSupplier<>(WorkerTaskEvent.class)),
        TaskConfig(new GenericCodecSupplier<>(TaskConfig.class)),
        WillConfig(new GenericCodecSupplier<>(WillConfig.class)),
        ClientTaskEvent(new GenericCodecSupplier<>(ClientTaskEvent.class)),
        TaskSchedule(new GenericCodecSupplier<>(TaskSchedule.class)),
        MqttBroker(new GenericCodecSupplier<>(MqttBroker.class)),
        TaskInfoMetadata(new GenericCodecSupplier<>(TaskInfoMetadata.class)),

        ClusterNodeInfo(new GenericCodecSupplier<>(ClusterNodeInfo.class)),
        NodeInfo(new GenericCodecSupplier<>(NodeInfo.class)),
        MemoryInfo(new GenericCodecSupplier<>(ClusterNodeInfo.MemoryInfo.class)),
        CpuInfo(new GenericCodecSupplier<>(ClusterNodeInfo.CpuInfo.class)),


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
    }
    