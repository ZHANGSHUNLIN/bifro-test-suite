package com.baidu.duhome.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;


// --- 内部类：用于封装分页信息 ---
    @Setter
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public class PageInfo<T> {
        private java.util.List<T> content; // 当前页的内容
        private long totalElements; // 总记录数
        private int totalPages; // 总页数
        private int size; // 每页大小
        private int number; 
        private int numberOfElements; 
        private boolean first = false;
        private boolean last = false;

        // 在构造 PageInfo 后，手动设置 first 和 last
        public PageInfo() {}

        // 一个便捷的构建方法，可以放在 PageInfo 内部
        public static <T> PageInfo<T> of(org.springframework.data.domain.Page<T> page) {
            PageInfo<T> pageInfo = new PageInfo<>();
            pageInfo.setContent(page.getContent()); // 当前页数据
            pageInfo.setTotalElements(page.getTotalElements()); // 总记录数
            pageInfo.setTotalPages(page.getTotalPages()); // 总页数
            pageInfo.setSize(page.getSize()); // 每页大小
            pageInfo.setNumber(page.getNumber()); // 当前页码 (从0开始)
            pageInfo.setNumberOfElements(page.getNumberOfElements()); // 当前页实际元素数量
            pageInfo.setFirst(page.isFirst());
            pageInfo.setLast(page.isLast());
            return pageInfo;
        }
    }