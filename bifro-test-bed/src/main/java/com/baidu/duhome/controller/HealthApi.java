package com.baidu.duhome.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "健康检查", description = "系统健康状态相关接口")
@RestController
public class HealthApi {

    @Operation(summary = "健康检查", description = "检查服务是否正常运行")
    @ApiResponse(responseCode = "200", description = "服务正常")
    @GetMapping("/health")
    public String health() {
        return "ok";
    }

}
