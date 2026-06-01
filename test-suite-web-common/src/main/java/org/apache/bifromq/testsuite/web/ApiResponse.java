/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.springframework.data.domain.Page;
import reactor.core.publisher.Mono;

@Slf4j
@Setter
@Getter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;


    public static <T> ApiResponse<T> success(T data) {
        return success(data, "success");
    }

    public static <T> ApiResponse<T> success(CompletableFuture<T> data) {
        try {
            if (data == null) {
                return success(null, "success");
            }
            return success(data.orTimeout(3, TimeUnit.SECONDS).get(), "success");
        } catch (InterruptedException | ExecutionException e) {
            log.debug("Data retrieval timeout: {}", e.getMessage());
            return error(Messages.get("error.data.timeout"));
        }
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(500, message);
    }


    public static <T> ApiResponse<PageInfo<T>> pageSuccess(Page<T> page) {
        PageInfo<T> pageInfo = new PageInfo<>();
        pageInfo.setContent(getPage(page).getContent());
        pageInfo.setTotalElements(page.getTotalElements());
        pageInfo.setTotalPages(page.getTotalPages());
        pageInfo.setSize(page.getSize());
        pageInfo.setNumber(page.getNumber());
        pageInfo.setNumberOfElements(page.getNumberOfElements());
        ApiResponse<PageInfo<T>> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage(Messages.get("msg.query.success"));
        response.setData(pageInfo);
        return response;
    }

    public static <S, T> ApiResponse<PageInfo<T>> pageSuccess(Page<S> page, @NonNull Function<S, T> converter) {
        List<T> content = page.getContent().stream().map(converter).toList();
        PageInfo<T> pageInfo = new PageInfo<>();
        pageInfo.setContent(content);
        pageInfo.setTotalElements(page.getTotalElements());
        pageInfo.setTotalPages(page.getTotalPages());
        pageInfo.setSize(page.getSize());
        pageInfo.setNumber(page.getNumber());
        pageInfo.setNumberOfElements(page.getNumberOfElements());
        ApiResponse<PageInfo<T>> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage(Messages.get("msg.query.success"));
        response.setData(pageInfo);
        return response;
    }


    public static <T> ApiResponse<PageInfo<T>> pageSuccess(List<T> content, long total, int pageNum, int pageSize) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        PageInfo<T> pageInfo = new PageInfo<>();
        pageInfo.setContent(content);
        pageInfo.setTotalElements(total);
        pageInfo.setTotalPages(totalPages);
        pageInfo.setSize(pageSize);
        pageInfo.setNumber(pageNum - 1);
        pageInfo.setNumberOfElements(content.size());
        ApiResponse<PageInfo<T>> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage(Messages.get("msg.query.success"));
        response.setData(pageInfo);
        return response;
    }


    public static <T> Mono<ApiResponse<PageInfo<T>>> pageSuccessMono(Mono<Page<T>> pageMono) {
        return pageMono.map(page -> pageSuccess(page));
    }


    public static <S, T> Mono<ApiResponse<PageInfo<T>>> pageSuccessMono(Mono<Page<S>> pageMono,
                                                                        @NonNull Function<S, T> converter) {
        return pageMono.map(page -> pageSuccess(page, converter));
    }

    private static <T> Page<T> getPage(Page<T> page) {
        return page;
    }

    public boolean isSuccess() {
        return this.code == 200;
    }
}
