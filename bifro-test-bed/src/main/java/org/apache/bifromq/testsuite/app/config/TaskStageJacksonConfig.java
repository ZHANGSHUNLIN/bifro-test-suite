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

package org.apache.bifromq.testsuite.app.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import org.apache.bifromq.testsuite.TaskStage;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskStageJacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer taskStageJacksonCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("TaskStageModule");
            module.addSerializer(TaskStage.class, new TaskStageSerializer());
            module.addDeserializer(TaskStage.class, new TaskStageDeserializer());
            builder.modulesToInstall(module);
        };
    }

    static class TaskStageSerializer extends StdSerializer<TaskStage> {

        TaskStageSerializer() {
            super(TaskStage.class);
        }

        @Override
        public void serialize(TaskStage value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
            if (value == TaskStage.STARTING) {
                gen.writeString("START");
            } else {
                gen.writeString(value.name());
            }
        }
    }

    static class TaskStageDeserializer extends StdDeserializer<TaskStage> {

        TaskStageDeserializer() {
            super(TaskStage.class);
        }

        @Override
        public TaskStage deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getText();
            if ("START".equals(value)) {
                return TaskStage.STARTING;
            }
            return TaskStage.valueOf(value);
        }
    }
}
