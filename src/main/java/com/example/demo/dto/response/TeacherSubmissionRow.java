package com.example.demo.dto.response;

import com.example.demo.domain.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một dòng trong danh sách bài nộp mà người ra đề nhìn thấy. */
@Data
@Builder
public class TeacherSubmissionRow {

    private Integer submissionId;

    private String studentId;
    private String studentName;
    private String studentEmail;

    private Integer examId;
    private String examTitle;

    /** Lượt làm thứ mấy, đếm từ 1. Một thí sinh có thể có nhiều dòng trên cùng một đề. */
    private Integer attemptNumber;

    private SubmissionStatus status;

    /** Server tự nộp khi hết giờ — đáng chú ý khi chữa bài (thí sinh không kịp làm). */
    private boolean autoSubmitted;

    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;

    /** Số phút làm bài. null khi bài chưa nộp. */
    private Long durationMinutes;

    private BigDecimal totalScore;
    private BigDecimal maxScore;

    /** Phần trăm điểm, làm tròn. null khi đề chưa có thang điểm. */
    private Integer percent;

    private int totalQuestions;
    private int answeredQuestions;
    private int correctAnswers;
}
