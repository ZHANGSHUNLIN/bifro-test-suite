/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.chaos;

public record ChaosContext(
    
    RawMqttConnection connection,
    
    int startPacketId,

    String topic,
    
    int maxInflightWindow,

    int maxPacketSize
) {
    
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private RawMqttConnection connection;
        private int startPacketId;
        private String topic;
        private int maxInflightWindow;
        private int maxPacketSize;

        public Builder connection(RawMqttConnection connection) {
            this.connection = connection;
            return this;
        }

        public Builder startPacketId(int startPacketId) {
            this.startPacketId = startPacketId;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder maxInflightWindow(int maxInflightWindow) {
            this.maxInflightWindow = maxInflightWindow;
            return this;
        }

        public Builder maxPacketSize(int maxPacketSize) {
            this.maxPacketSize = maxPacketSize;
            return this;
        }

        public ChaosContext build() {
            return new ChaosContext(connection, startPacketId, topic, maxInflightWindow, maxPacketSize);
        }
    }
}
