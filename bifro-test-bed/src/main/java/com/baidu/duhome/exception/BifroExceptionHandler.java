package com.baidu.duhome.exception;

import com.baidu.duhome.bean.CommonResp;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Component
@RestControllerAdvice
public class BifroExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<CommonResp> handleApiException(ApiException ex) {
        return ResponseEntity.ok(CommonResp.error(ex.getMessage()));
    }

}
