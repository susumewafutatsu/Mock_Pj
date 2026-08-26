
package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Một đáp án gửi lên kèm câu hỏi. */
@Data
public class AnswerPayload {

    /** Null = đáp án mới; có giá trị = cập nhật đáp án đang có. */
    private Integer answerId;

    @NotBlank(message = "Nội dung đáp án không được để trống")
    private String answerContent;

    private boolean correct = false;
}
