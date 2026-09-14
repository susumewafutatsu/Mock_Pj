package com.example.demo.controller;

import com.example.demo.dto.request.ChangeRoleRequest;
import com.example.demo.dto.request.LockUserRequest;
import com.example.demo.dto.response.AdminStatsResponse;
import com.example.demo.dto.response.AdminUserPageResponse;
import com.example.demo.dto.response.AdminUserResponse;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trang quản trị. Cả nhánh /api/admin/** chỉ cho vai ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public ApiResponse<AdminStatsResponse> stats() {
        return ApiResponse.success(adminService.getStats());
    }

    @GetMapping("/users")
    public ApiResponse<AdminUserPageResponse> users(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean locked,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(
                adminService.listUsers(me.getUsername(), role, locked, q, page, size));
    }

    @PutMapping("/users/{userId}/role")
    public ApiResponse<AdminUserResponse> changeRole(@PathVariable String userId,
                                                     @RequestBody ChangeRoleRequest request,
                                                     @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã đổi vai trò",
                adminService.changeRole(me.getUsername(), userId, request.getRole()));
    }

    @PostMapping("/users/{userId}/lock")
    public ApiResponse<AdminUserResponse> lock(@PathVariable String userId,
                                               @RequestBody(required = false) LockUserRequest request,
                                               @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã khoá tài khoản",
                adminService.lock(me.getUsername(), userId, request == null ? null : request.getReason()));
    }

    @PostMapping("/users/{userId}/unlock")
    public ApiResponse<AdminUserResponse> unlock(@PathVariable String userId,
                                                 @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã mở khoá tài khoản",
                adminService.unlock(me.getUsername(), userId));
    }
}
