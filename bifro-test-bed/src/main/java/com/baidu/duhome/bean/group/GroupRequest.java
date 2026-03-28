package com.baidu.duhome.bean.group;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分组请求 DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupRequest {

    /**
     * 分组名称
     */
    @NotBlank(message = "分组名称不能为空")
    private String name;

    /**
     * 分组描述
     */
    private String description;
}
