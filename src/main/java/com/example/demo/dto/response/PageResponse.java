package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Bao phân trang dùng chung cho các API trả về danh sách.
 * Không serialize trực tiếp {@link Page} vì cấu trúc JSON của nó không ổn định giữa các version Spring.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return PageResponse.<T>builder()
                .items(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    /** Trang rỗng, dùng khi biết chắc không có kết quả mà không cần truy vấn DB. */
    public static <T> PageResponse<T> empty(Pageable pageable) {
        return PageResponse.<T>builder()
                .items(List.of())
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalItems(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .build();
    }
}
