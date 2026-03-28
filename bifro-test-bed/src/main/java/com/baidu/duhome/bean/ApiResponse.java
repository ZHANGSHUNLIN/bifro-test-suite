package com.baidu.duhome.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import reactor.core.publisher.Mono;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 通用响应包装类
 */
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

    // --- 通用的成功/失败方法 ---

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
            log.debug("获取数据超时,{}", e.getMessage());
            return error("获取数据超时");
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

    // --- 新增：分页响应方法 ---
    // 这个方法专门处理 Spring Data 的 Page 对象
    public static <T> ApiResponse<PageInfo<T>> pageSuccess(Page<T> page) {
        PageInfo<T> pageInfo = new PageInfo<>();
        pageInfo.setContent(getPage(page).getContent()); // 当前页数据
        pageInfo.setTotalElements(page.getTotalElements()); // 总记录数
        pageInfo.setTotalPages(page.getTotalPages()); // 总页数
        pageInfo.setSize(page.getSize()); // 每页大小
        pageInfo.setNumber(page.getNumber()); // 当前页码 (从0开始)
        pageInfo.setNumberOfElements(page.getNumberOfElements()); // 当前页实际元素数量
        ApiResponse<PageInfo<T>> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("查询成功");
        response.setData(pageInfo);
        return response;
    }

    public static <S, T> ApiResponse<PageInfo<T>> pageSuccess(Page<S> page, @NonNull Function<S, T> converter) {
        List<T> content = page.getContent().stream().map(converter).toList();
        PageInfo<T> pageInfo = new PageInfo<>();
        pageInfo.setContent(content); // 当前页数据
        pageInfo.setTotalElements(page.getTotalElements()); // 总记录数
        pageInfo.setTotalPages(page.getTotalPages()); // 总页数
        pageInfo.setSize(page.getSize()); // 每页大小
        pageInfo.setNumber(page.getNumber()); // 当前页码 (从0开始)
        pageInfo.setNumberOfElements(page.getNumberOfElements()); // 当前页实际元素数量
        ApiResponse<PageInfo<T>> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("查询成功");
        response.setData(pageInfo);
        return response;
    }

    /**
     * 手动分页响应方法 - 用于响应式 MongoDB 等不支持原生分页的场景
     */
    public static <T> ApiResponse<PageInfo<T>> pageSuccess(List<T> content, long total, int pageNum, int pageSize) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        PageInfo<T> pageInfo = new PageInfo<>();
        pageInfo.setContent(content);
        pageInfo.setTotalElements(total);
        pageInfo.setTotalPages(totalPages);
        pageInfo.setSize(pageSize);
        pageInfo.setNumber(pageNum - 1); // Spring Data Page 的页码从0开始
        pageInfo.setNumberOfElements(content.size());
        ApiResponse<PageInfo<T>> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("查询成功");
        response.setData(pageInfo);
        return response;
    }

    /**
     * 响应式分页响应方法 - 接受 Mono<Page>
     */
    public static <T> Mono<ApiResponse<PageInfo<T>>> pageSuccessMono(Mono<Page<T>> pageMono) {
        return pageMono.map(page -> pageSuccess(page));
    }

    /**
     * 响应式分页响应方法 - 接受 Mono<Page> 并支持类型转换
     */
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
