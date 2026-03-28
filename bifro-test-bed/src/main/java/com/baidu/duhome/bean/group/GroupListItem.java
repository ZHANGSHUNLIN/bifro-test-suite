package com.baidu.duhome.bean.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 分组列表项 DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupListItem {

    /**
     * 分组ID
     */
    private String id;

    /**
     * 分组类型：BROKER- Broker分组, TASK- 任务分组
     */
    private String type;

    /**
     * 分组名称
     */
    private String name;

    /**
     * 分组描述
     */
    private String description;

    /**
     * 该分组下的 Broker 或任务数量
     * 根据类型返回不同的字段名，前端使用 brokerCount 和 taskCount
     */
    @JsonProperty("brokerCount")
    public Long getBrokerCount() {
        return com.baidu.duhome.cluster.GroupManager.TYPE_BROKER.equals(type) ? count : 0L;
    }

    @JsonProperty("taskCount")
    public Long getTaskCount() {
        return com.baidu.duhome.cluster.GroupManager.TYPE_TASK.equals(type) ? count : 0L;
    }

    /**
     * 内部使用的 count 字段
     */
    private Long count;

    /**
     * 创建时间
     */
    private Instant createdAt;
}
