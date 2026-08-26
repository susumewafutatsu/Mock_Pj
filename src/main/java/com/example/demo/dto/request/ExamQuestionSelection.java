
package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Một câu hỏi được chọn đưa vào đề thi. */
@Data
public class ExamQuestionSelection {

    @NotNull(message = "Phải chỉ định câu hỏi")
    private Integer questionId;

    private BigDecimal points = new BigDecimal("1.00");

    /** Thứ tự hiển thị trong đề. Null = xếp theo thứ tự gửi lên. */
    private Integer questionOrder;
}
