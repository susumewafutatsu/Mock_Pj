package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Một thí sinh đang ở trong (ít nhất) một phòng của người ra đề, kèm tình hình làm bài. */
@Data
@Builder
public class TeacherStudentRow {

    private String userId;
    private String fullName;
    private String email;
    private String avatarUrl;

    /** Tên các phòng của người ra đề mà thí sinh này đang ở trong. */
    private List<String> roomNames;

    /** Số đề được giao trong các phòng đó, và số đề đã nộp ít nhất một lần. */
    private int assignedExams;
    private int submittedExams;

    /** Điểm trung bình theo phần trăm trên các bài đã nộp. null khi chưa nộp bài nào. */
    private Integer averagePercent;

    private LocalDateTime lastSubmittedAt;
}
