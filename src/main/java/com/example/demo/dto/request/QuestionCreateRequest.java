
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

@Data
public class QuestionCreateRequest {

    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    private String content;

    @NotNull(message = "Phải chọn loại câu hỏi")
    private QuestionType questionType = QuestionType.MULTIPLE_CHOICE;

    @Min(value = 1, message = "Độ khó từ 1 đến 5")
    @Max(value = 5, message = "Độ khó từ 1 đến 5")
    private Integer difficultyLevel;

    /** Kỹ năng JLPT (文字・語彙 / 文法 / 読解 / 聴解). */
    private JlptSkill skill;

    /** Đoạn văn mà câu này hỏi về. Chỉ dùng cho câu đọc hiểu. */
    private Integer passageId;

    /** File nghe cho câu 聴解 — đường dẫn nhận được từ POST /api/teacher/media/audio. */
    private String audioUrl;

    /** Số lần được nghe. Để trống = 1, đúng như kỳ thi thật. */
    @jakarta.validation.constraints.Min(value = 1, message = "Số lần nghe tối thiểu là 1")
    @jakarta.validation.constraints.Max(value = 5, message = "Số lần nghe tối đa là 5")
    private Integer maxAudioPlays;

    private String explanation;

    private boolean aiGenerated = false;

    /** Bắt buộc với MULTIPLE_CHOICE / MATCHING, bỏ trống với ESSAY. */
    @Valid
    private List<AnswerPayload> answers = new ArrayList<>();
}
