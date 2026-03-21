package com.baidu.duhome.database.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "mqtt_broker")
public class MqttBroker implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @Id
    private String id;

    private String brokerId ;

    private String name;

    private String host;

    private Integer port;

    private String description;

    private Boolean enabled;

    private Integer maxConnections;

    private Instant createdAt;

    private Instant updatedAt;

}