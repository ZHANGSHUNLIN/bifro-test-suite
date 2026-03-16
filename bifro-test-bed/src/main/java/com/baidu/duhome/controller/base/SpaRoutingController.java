package com.baidu.duhome.controller.base;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

// 注意：这里建议改为 @RestController，或者确保方法上有 @ResponseBody 语义
@RestController
public class SpaRoutingController {

    // 匹配 /admin
    @GetMapping("/admin")
    public Mono<Resource> adminRoot() {
        // 直接返回类路径下的静态文件
        return Mono.just(new ClassPathResource("static/admin/index.html"));
    }

    // 匹配 /admin/xxx （不含 . 的路径，避免拦截静态资源如 .js/.css）
    @GetMapping({
            "/admin/{path:[^\\.]*}",
            "/admin/{path1:[^\\.]*}/{path2:[^\\.]*}"
    })
    public Mono<Resource> adminSpaRoutes() {
        return Mono.just(new ClassPathResource("static/admin/index.html"));
    }
}