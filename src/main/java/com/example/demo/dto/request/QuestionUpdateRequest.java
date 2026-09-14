
package com.example.demo.dto.request;

import com.example.demo.domain.enums.JlptSkill;
import com.example.demo.domain.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Sửa câu hỏi trong ngân hàng. */
@Data
public class QuestionUpdateRequest {

    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    private String content;

    @NotNull(message = "Phải chọn loại câu hỏi")
    private QuestionType questionType;

    @Min(value = 1, message = "Độ khó từ 1 đến 5")
    @Max(value = 5, message = "Độ khó từ 1 đến 5")
    private Integer difficultyLevel;

    /** Kỹ năng JLPT (文字・語彙 / 文法 / 読解 / 聴解). Để trống = chưa phân loại. */
    private JlptSkill skill;

    /** Đoạn văn của câu đọc hiểu. Gửi null để gỡ câu khỏi bài đọc. */
    private Integer passageId;

    /** File nghe cho câu 聴解 — đường dẫn nhận được từ POST /api/teacher/media/audio. */
    private String audioUrl;

    /** Số lần được nghe. Để trống = 1, đúng như kỳ thi thật. */
    @jakarta.validation.constraints.Min(value = 1, message = "Số lần nghe tối thiểu là 1")
    @jakarta.validation.constraints.Max(value = 5, message = "Số lần nghe tối đa là 5")
    private Integer maxAudioPlays;

    private String explanation;

    /** Danh sách đáp án SAU khi sửa. */
    @Valid
    private List<AnswerPayload> answers = new ArrayList<>();
}
