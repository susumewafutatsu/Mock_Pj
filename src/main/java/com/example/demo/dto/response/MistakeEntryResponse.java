package com.example.demo.dto.response;

import com.example.demo.domain.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Một câu trong sổ tay câu sai, đủ dữ liệu để làm lại ngay tại chỗ. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MistakeEntryResponse {

    private Integer questionId;

    private String content;

    private QuestionType questionType;

    private Integer difficultyLevel;

    /** Đã sai câu này bao nhiêu lần từ trước tới nay. */
    private Integer wrongCount;

    /** Đang đúng liên tiếp mấy lần. Đủ 2 là câu rời khỏi sổ tay. */
    private Integer correctStreak;

    private LocalDateTime lastWrongAt;

    private LocalDateTime nextReviewAt;

    /** Đã tới lúc nên ôn lại chưa. Server tự tính, client không so giờ lại. */
    private boolean due;

    /** Rỗng với câu tự luận — dạng đó không có lựa chọn để chấm máy. */
    private List<StudyOptionView> options;
}
