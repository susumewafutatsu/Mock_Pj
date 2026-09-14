package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Số liệu tổng quan của trang quản trị. */
@Data
@Builder
public class AdminStatsResponse {

    // Người dùng
    private long totalUsers;
    private long students;
    private long teachers;
    private long admins;
    private long lockedUsers;
    private long newUsersLast7Days;

    // Nội dung
    private long exams;
    private long publicExams;
    private long questionBanks;
    private long rooms;
    private long pendingCourses;

    // Hoạt động thi ngay lúc này
    private long sessionsInProgress;
    /** Đang làm nhưng im lặng quá ngưỡng — nghi rớt mạng. */
    private long sessionsAtRisk;
    private long submittedLast24h;

    private LocalDateTime serverTime;
}
