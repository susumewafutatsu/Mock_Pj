package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Một lần thí sinh chọn / sửa đáp án của một câu hỏi. */
@Data
public class SaveAnswerRequest {

    @NotNull(message = "Thiếu questionId")
    private Integer questionId;

    /** Đáp án được chọn, lấy từ snapshot của đề thi (không phải AnswerID trong ngân hàng câu hỏi). */
    private Integer snapshotAnswerId;

    /** Nội dung câu tự luận. Null hoặc rỗng = xoá phần đã viết. */
    private String essayResponse;
}
