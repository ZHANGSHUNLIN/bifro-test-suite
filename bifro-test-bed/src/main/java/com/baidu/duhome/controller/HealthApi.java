package com.baidu.duhome.controller;

import com.baidu.duhome.local.LocalTaskCoordinator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Tag(name = "健康检查", description = "系统健康状态相关接口")
@RestController
public class HealthApi {

    @Resource
    private LocalTaskCoordinator localTaskCoordinator;


    @Operation(summary = "健康检查", description = "检查服务是否正常运行")
    @ApiResponse(responseCode = "200", description = "服务正常")
    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    @Operation(summary = "版本信息", description = "获取当前构建版本号（构建时间）")
    @ApiResponse(responseCode = "200", description = "版本信息")
    @GetMapping("/version")
    public VersionInfo version() {
        return new VersionInfo("-1", "0", "构建时间");
    }


    @GetMapping("/running_tasks")
    public Object info(){
        return localTaskCoordinator.getRunningTaskMap();
    }

    @Data
    @AllArgsConstructor
    public static class VersionInfo {
        private String version;
        private String buildTime;
        private String description;
    }

}
