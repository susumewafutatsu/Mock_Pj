package com.example.demo.service;

import com.example.demo.dto.response.AdminStatsResponse;
import com.example.demo.dto.response.AdminUserPageResponse;
import com.example.demo.dto.response.AdminUserResponse;

/** Trang quản trị: số liệu tổng quan và quản lý tài khoản. */
public interface AdminService {

    AdminStatsResponse getStats();

    /**
     * @param role   STUDENT / TEACHER / ADMIN, null = mọi vai trò
     * @param locked null = cả hai, true = chỉ tài khoản bị khoá
     * @param q      tìm theo tên hoặc email, không phân biệt hoa thường
     * @param page   đếm từ 0
     */
    AdminUserPageResponse listUsers(String adminEmail, String role, Boolean locked,
                                    String q, int page, int size);

    AdminUserResponse changeRole(String adminEmail, String userId, String role);

    AdminUserResponse lock(String adminEmail, String userId, String reason);

    AdminUserResponse unlock(String adminEmail, String userId);
}
