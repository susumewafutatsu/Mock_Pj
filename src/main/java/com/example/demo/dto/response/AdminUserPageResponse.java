package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Một trang bảng người dùng. */
@Data
@Builder
public class AdminUserPageResponse {
    private List<AdminUserResponse> users;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private Map<String, Long> roleCounts;
    private long lockedCount;
}
