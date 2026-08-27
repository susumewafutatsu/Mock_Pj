package com.example.demo.dto.response;

import com.example.demo.domain.enums.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

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

    private Boolean correct;
    private BigDecimal scoreEarned;

    /** Chỉ trả về khi bài đã chấm xong, tránh lộ đáp án của phiên còn dở. */
    private String correctAnswerContent;
    private String explanation;

    /** Câu tự luận chờ giáo viên / AI chấm. */
    private boolean awaitingManualGrading;
}
