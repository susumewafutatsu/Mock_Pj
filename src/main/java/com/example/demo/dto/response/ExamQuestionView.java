package com.example.demo.dto.response;

import com.example.demo.domain.enums.JlptSkill;
import com.example.demo.domain.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Câu hỏi nhìn từ phía thí sinh đang làm bài, kèm phần đã trả lời (nếu có). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamQuestionView {

    private Integer questionId;
    private Integer questionOrder;
    private BigDecimal points;
    private String content;
    private QuestionType questionType;
    private List<ExamOptionView> options;

    /** Phần thi chứa câu này. null = đề không chia phần, một đồng hồ duy nhất. */
    private Integer sectionId;

    /** Kỹ năng JLPT — phòng thi dùng để nhóm và để hiện nhãn 文法 / 読解… */
    private JlptSkill skill;

    /** Bài đọc mà câu này hỏi về. */
    private Integer passageId;
    private String passageTitle;
    private String passageContent;

    /** File nghe (聴解). Client KHÔNG phát thẳng. */
    private String audioUrl;
    private Integer maxAudioPlays;
    /** Số lượt đã nghe trong lượt làm này. */
    private Integer audioPlays;

    // ── Phần thí sinh đã làm, phục hồi từ SubmissionDetails ────────────────
    private Integer selectedSnapshotAnswerId;
    private String essayResponse;
    private LocalDateTime answeredAt;
}
