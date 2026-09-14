package com.example.demo.dto.response;

import com.example.demo.domain.enums.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Kết quả của một câu trong bài đã nộp. */
@Data
@Builder
public class ResultDetailView {

    private Integer questionId;
    private Integer questionOrder;
    private String content;
    private QuestionType questionType;
    private BigDecimal points;

    private Integer selectedSnapshotAnswerId;
    private String selectedAnswerContent;
    private String essayResponse;

    /** Toàn bộ lựa chọn của câu này, theo snapshot của đề. */
    private List<ExamOptionView> options;

    /** ID của đáp án đúng. null khi đề tắt xem đáp án, hoặc câu tự luận. */
    private Integer correctSnapshotAnswerId;

    private Boolean correct;
    private BigDecimal scoreEarned;

    /** Chỉ trả về khi bài đã chấm xong, tránh lộ đáp án của phiên còn dở. */
    private String correctAnswerContent;
    private String explanation;

    /** Câu tự luận chờ người ra đề / AI chấm. */
    private boolean awaitingManualGrading;
}
