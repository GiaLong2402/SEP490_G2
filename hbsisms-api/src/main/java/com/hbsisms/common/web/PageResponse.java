package com.hbsisms.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** List response shape expected by the frontend: 1-based {@code page}. */
public record PageResponse<T>(List<T> items, long total, int page, int pageSize) {

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }
}
