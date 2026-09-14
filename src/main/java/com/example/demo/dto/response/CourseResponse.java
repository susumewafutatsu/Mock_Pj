package com.example.demo.dto.response;

import com.example.demo.domain.enums.CourseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Một khoá học trong danh sách, kèm tiến độ của chính người đang xem. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseResponse {

    private Integer courseId;

    private String title;

    private String description;

    private Integer levelId;

    private String levelName;

    private String subjectName;

    private String authorName;

    private CourseStatus status;

    /** Lý do bị từ chối. Chỉ có giá trị với tác giả, và chỉ khi status = REJECTED. */
    private String reviewNote;

    private String reviewedByName;

    private LocalDateTime reviewedAt;

    private long totalLessons;

    /** Số người đang theo khoá. */
    private long enrolledCount;

    // ── Riêng người đang xem ────────────────────────────────────────────

    /** Người đang xem đã ghi danh chưa. */
    private boolean enrolled;

    /** Số bài người đang xem đã hoàn thành. */
    private long completedLessons;

    /** Phần trăm hoàn thành, làm tròn xuống. */
    private int progressPercent;

    /** Người đang xem là tác giả. */
    private boolean author;
}
