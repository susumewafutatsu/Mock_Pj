package com.example.demo.dto.request;

import com.example.demo.domain.enums.ReviewGrade;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Người học tự đánh giá mình nhớ thẻ tới đâu sau khi lật. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewGradeRequest {

    @NotNull(message = "Phải chọn mức độ nhớ")
    private ReviewGrade grade;
}
