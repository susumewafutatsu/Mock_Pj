package com.example.demo.dto.response;

import com.example.demo.domain.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Kết quả một bài đã nộp. */
@Data
@Builder
public class ExamResultResponse {

    private Integer submissionId;
    private Integer examId;
    private String examTitle;

    /** Lượt làm thứ mấy, đếm từ 1. Trang lịch sử dùng để tách các lần làm cùng một đề. */
    private Integer attemptNumber;

    /** Người ra đề có cho xem đáp án đúng + giải thích không. */
    private boolean reviewAllowed;

    /** Số lượt đã dùng / tối đa. {@code maxAttempts} null = không giới hạn. */
    private Long attemptsUsed;
    private Integer maxAttempts;

    /** Còn được làm lại đề này không — để trang kết quả hiện nút "Làm lại". */
    private boolean canRetake;

    private SubmissionStatus status;

    /** Bài do server tự nộp khi hết giờ. */
    private boolean autoSubmitted;

    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;

    private BigDecimal totalScore;
    private BigDecimal maxScore;

    private int totalQuestions;
    private int answeredQuestions;
    private int correctAnswers;

    /** Còn câu tự luận chưa chấm — điểm hiện tại chưa phải điểm cuối cùng. */
    private boolean awaitingManualGrading;

    private List<ResultDetailView> details;

    /** Bảng điểm kiểu JLPT: điểm từng nhóm, tổng 0–180, kết luận Đỗ/Trượt. */
    private JlptScoreView jlpt;
}
