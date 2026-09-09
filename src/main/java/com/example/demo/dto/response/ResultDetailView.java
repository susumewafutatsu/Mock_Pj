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

    /**
     * Toàn bộ lựa chọn của câu này, theo snapshot của đề. Rỗng với câu tự luận.
     *
     * Trang xem lại cần cả các phương án thí sinh KHÔNG chọn: nhìn lại bốn đáp
     * án rồi thấy mình đã loại nhầm cái nào mới là lúc học được, còn một dòng
     * "bạn chọn B, đáp án đúng là C" thì không nói lên điều gì.
     *
     * Danh sách này không mang cờ đúng/sai (xem {@link ExamOptionView}); đáp án
     * đúng nhận diện bằng {@link #correctSnapshotAnswerId}, và chỉ được điền khi
     * người ra đề cho phép xem đáp án.
     */
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
