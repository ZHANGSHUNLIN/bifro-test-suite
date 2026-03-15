//package com.baidu.duhome.config;
//
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
//@Configuration
//public class AdminSpaWebConfig implements WebMvcConfigurer {
//
//    @Override
//    public void addViewControllers(ViewControllerRegistry registry) {
//        registry.addViewController("/admin")
//                .setViewName("forward:/admin/index.html");
//        registry.addViewController("/admin/{path:[^\\.]+}")
//                .setViewName("forward:/admin/index.html");
//        registry.addViewController("/admin/{path1:[^\\.]+}/{path2:[^\\.]+}")
//                .setViewName("forward:/admin/index.html");
//    }
//}