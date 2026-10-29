package com.org.ecommerce.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Stable pagination envelope (decoupled from Spring's Page JSON shape). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static final int MAX_PAGE_SIZE = 100;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /** Builds a Pageable with the size clamped to [1, 100] and page >= 0. */
    public static Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), sort);
    }
}
