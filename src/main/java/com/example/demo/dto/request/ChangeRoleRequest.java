package com.example.demo.dto.request;

import lombok.Data;

/** Admin đổi vai trò một tài khoản. */
@Data
public class ChangeRoleRequest {
    private String role;
}
