package com.baidu.duhome.exception;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiException extends RuntimeException {

    public ApiException(String message) {
        super(message);
    }


    public ApiException(Throwable cause) {
        super(cause);
        log.error("task execution error", cause);
    }


}
