package com.monicalab.common.dto;

import java.util.List;
import java.util.function.Function;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
public class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean last;

    private PageResponse(List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.last = last;
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return of(page, Function.identity());
    }

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        List<T> content = page.getContent().stream().map(mapper).toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
    }
}
