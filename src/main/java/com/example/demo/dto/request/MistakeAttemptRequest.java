package com.example.demo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một lần làm lại câu sai trong sổ tay. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MistakeAttemptRequest {

    /** Bắt buộc với câu trắc nghiệm. */
    private Integer selectedAnswerId;

    /** Chỉ dùng cho câu tự luận. Bỏ trống thì coi như chưa nhớ được. */
    private Boolean selfCorrect;
}
