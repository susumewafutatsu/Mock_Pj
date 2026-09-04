package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ClassStudentResponse {

    private String studentId;
    private String fullName;
    private String email;
    private String avatarUrl;
    private LocalDateTime joinedAt;
}
