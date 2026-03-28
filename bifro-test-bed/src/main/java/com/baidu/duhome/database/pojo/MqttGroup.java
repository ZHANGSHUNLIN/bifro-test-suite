package com.baidu.duhome.database.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * MQTT 分组实体（支持 Broker 分组和任务分组）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "mqtt_group")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttGroup implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    /**
     * 分组类型：BROKER- Broker分组, TASK- 任务分组
     */
    private String type;

    /**
     * 分组名称
     */
    @NotBlank(message = "分组名称不能为空")
    private String name;

    /**
     * 分组描述
     */
    private String description;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;
}
