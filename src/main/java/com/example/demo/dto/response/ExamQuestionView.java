package com.example.demo.dto.response;

import com.example.demo.domain.enums.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Câu hỏi nhìn từ phía học sinh đang làm bài, kèm phần đã trả lời (nếu có).
 *
 * Nội dung lấy từ snapshot của đề thi, không đọc ngân hàng câu hỏi — giáo viên
 * sửa câu hỏi giữa lúc học sinh đang thi cũng không làm đề đổi nội dung.
 * Phần {@code selectedSnapshotAnswerId} / {@code essayResponse} chính là thứ
 * giúp học sinh mất mạng quay lại thấy đúng những gì mình đã chọn.
 */
@Data
@Builder
public class ExamQuestionView {

    private Integer questionId;
    private Integer questionOrder;
    private BigDecimal points;
    private String content;
    private QuestionType questionType;
    private List<ExamOptionView> options;

    // ── Phần học sinh đã làm, phục hồi từ SubmissionDetails ────────────────
    private Integer selectedSnapshotAnswerId;
    private String essayResponse;
    private LocalDateTime answeredAt;
}
