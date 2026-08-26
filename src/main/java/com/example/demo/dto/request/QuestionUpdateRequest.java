
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

/**
 * Sửa câu hỏi trong ngân hàng. Không bị chặn kể cả khi câu hỏi đã nằm trong đề
 * thi đã phát hành: các đề đó đọc snapshot riêng nên điểm cũ không đổi.
 */
@Data
public class QuestionUpdateRequest {

    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    private String content;

    @NotNull(message = "Phải chọn loại câu hỏi")
    private QuestionType questionType;

    @Min(value = 1, message = "Độ khó từ 1 đến 5")
    @Max(value = 5, message = "Độ khó từ 1 đến 5")
    private Integer difficultyLevel;

    private String explanation;

    /**
     * Danh sách đáp án SAU khi sửa. Đáp án có answerId sẽ được cập nhật,
     * không có answerId là đáp án mới, đáp án cũ không xuất hiện ở đây bị xoá.
     */
    @Valid
    private List<AnswerPayload> answers = new ArrayList<>();
}
