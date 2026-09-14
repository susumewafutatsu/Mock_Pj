package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Một dòng trong bảng người dùng của trang quản trị. */
@Data
@Builder
public class AdminUserResponse {
    private String userId;
    private String fullName;
    private String email;
    private String role;
    private String authProvider;
    private String avatarUrl;
    private LocalDateTime createdAt;

    private boolean locked;
    private LocalDateTime lockedAt;
    private String lockReason;

    /** Admin không sửa được chính mình và các tài khoản quản trị khác qua giao diện. */
    private boolean editable;
}
