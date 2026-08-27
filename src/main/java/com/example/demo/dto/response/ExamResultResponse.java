package com.example.demo.dto.response;

import com.example.demo.domain.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Kết quả một bài đã nộp.
 *
 * Điểm được chấm theo snapshot đáp án của đề thi, nên kết quả này vẫn giải
 * thích được kể cả khi câu hỏi trong ngân hàng đã bị sửa về sau.
 */
@Data
@Builder
public class ExamResultResponse {

    private Integer submissionId;
    private Integer examId;
    private String examTitle;

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
}
