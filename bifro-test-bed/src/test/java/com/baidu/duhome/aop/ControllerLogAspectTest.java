//package com.baidu.duhome.aop;
//
//import com.baidu.duhome.bean.CommonResp;
//import com.baidu.duhome.bean.dto.TaskRequest;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.http.MediaType;
//import org.springframework.test.web.servlet.MockMvc;
//import org.springframework.test.web.servlet.MvcResult;
//
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
///**
// * Controller日志切面测试
// */
//@SpringBootTest
//@AutoConfigureMockMvc
//class ControllerLogAspectTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
////    @SpyBean
////    private ControllerLogAspect controllerLogAspect;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @Test
//    void testGetRequestLogging() throws Exception {
//        // 测试GET请求
//        MvcResult result = mockMvc.perform(get("/api/task/list"))
//                .andExpect(status().isOk())
//                .andReturn();
//
//        System.out.println("GET Response: " + result.getResponse().getContentAsString());
//    }
//
//    @Test
//    void testPostRequestLogging() throws Exception {
//        // 创建测试任务请求
//        TaskRequest taskRequest = new TaskRequest();
//
//        // 测试POST请求
//        String requestJson = objectMapper.writeValueAsString(taskRequest);
//
//        MvcResult result = mockMvc.perform(post("/api/task")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(requestJson))
//                .andExpect(status().isOk())
//                .andReturn();
//
//        System.out.println("POST Response: " + result.getResponse().getContentAsString());
//    }
//
//    @Test
//    void testGetTaskDetailsLogging() throws Exception {
//        // 测试GET带路径参数的请求
//        MvcResult result = mockMvc.perform(get("/api/task/test-task-id"))
//                .andExpect(status().isOk())
//                .andReturn();
//
//        System.out.println("GET with path variable Response: " + result.getResponse().getContentAsString());
//    }
//}
