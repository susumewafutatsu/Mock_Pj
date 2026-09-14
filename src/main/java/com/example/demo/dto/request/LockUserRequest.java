package com.example.demo.dto.request;

import lombok.Data;

/** Khoá một tài khoản. Lý do bắt buộc (kiểm ở AdminServiceImpl): người mở khoá sau này — có khi là một admin khác. */
@Data
public class LockUserRequest {
    private String reason;
}
