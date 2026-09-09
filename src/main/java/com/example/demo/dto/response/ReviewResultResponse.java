package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lịch mới của một thẻ sau khi người học tự đánh giá.
 *
 * Trả về đủ số liệu để màn hình nói được "gặp lại sau 6 ngày" — thấy khoảng
 * cách giãn ra là động lực đáng kể, và cũng là cách người học hiểu vì sao
 * bấm "Dễ" khác bấm "Khó".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResultResponse {

    private Integer itemId;

    /** Số ngày tới lần gặp lại. 0 nghĩa là quay lại ngay trong phiên hôm nay. */
    private Integer intervalDays;

    private LocalDateTime dueAt;

    private BigDecimal easeFactor;

    private Integer repetitions;

    /** Thẻ vừa vượt ngưỡng 21 ngày, tức đã coi như thuộc. */
    private boolean mature;

    /** Số thẻ còn lại trong hàng đợi hôm nay, để client cập nhật thanh tiến độ. */
    private long remainingDue;
}
