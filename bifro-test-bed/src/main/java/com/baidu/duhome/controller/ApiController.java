package com.baidu.duhome.controller;

import com.baidu.duhome.bean.ApiResponse;
import com.baidu.duhome.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

public interface ApiController {

    Logger log = LoggerFactory.getLogger(ApiController.class);

    /**
     * return 包装
     */
    default <T> CompletableFuture<ResponseEntity<T>> ret(CompletableFuture<T> completableFuture) {
        return completableFuture.handle((res, throwable) ->
                {
                    if (throwable != null) {
                        log.error("task execution error", throwable);
                        throw new ApiException(throwable.getMessage());
                    }
                    return ResponseEntity.ok(res);
                }
        );
    }


}
