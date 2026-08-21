
package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private String userId;
    private String fullName;
    private String email;
    private String role;
    private String avatarUrl;
    private LocalDateTime createdAt;
}