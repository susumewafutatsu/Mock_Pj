
package com.example.demo.dto.request;

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

    @NotNull(message = "Phải chỉ định ngân hàng câu hỏi")
    private Integer bankId;

    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    private String content;

    @NotNull(message = "Phải chọn loại câu hỏi")
    private QuestionType questionType = QuestionType.MULTIPLE_CHOICE;

    @Min(value = 1, message = "Độ khó từ 1 đến 5")
    @Max(value = 5, message = "Độ khó từ 1 đến 5")
    private Integer difficultyLevel;

    private String explanation;

    private boolean aiGenerated = false;

    /** Bắt buộc với MULTIPLE_CHOICE / MATCHING, bỏ trống với ESSAY. */
    @Valid
    private List<AnswerPayload> answers = new ArrayList<>();
}
