package com.baidu.duhome.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthApi {


    @GetMapping("/health")
    public String health() {
        return "ok";
    }


}
