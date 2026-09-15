package com.example.demo.dto.request;

import com.example.demo.domain.enums.JlptSkill;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Yêu cầu Gemini sinh câu hỏi từ nội dung văn bản đã trích xuất (PDF/Word). */
@Data
public class AiGenerateRequest {

    @NotBlank(message = "Chưa có nội dung văn bản để sinh câu hỏi")
    private String text;

    @Min(value = 1, message = "Phải sinh ít nhất 1 câu hỏi")
    @Max(value = 20, message = "Tối đa 20 câu hỏi mỗi lần")
    private int questionCount = 5;

    @Min(value = 1, message = "Độ khó từ 1 đến 5")
    @Max(value = 5, message = "Độ khó từ 1 đến 5")
    private Integer difficultyLevel;

    private JlptSkill skill;
}
