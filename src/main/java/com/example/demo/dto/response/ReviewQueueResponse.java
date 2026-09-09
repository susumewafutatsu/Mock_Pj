package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Hàng đợi ôn thẻ của hôm nay, kèm bối cảnh để người học biết mình đang ở đâu. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewQueueResponse {

    /** Các thẻ của phiên này, đã cắt theo hạn mức ngày. */
    private List<ReviewCardResponse> cards;

    /** Tổng số thẻ đã tới hạn, có thể lớn hơn số thẻ trả về. */
    private long dueCount;

    /** Trong hàng đợi này có bao nhiêu thẻ chưa từng ôn. */
    private long newCount;

    /** Tổng số thẻ đang học. */
    private long totalCards;

    /** Số thẻ đã vào trí nhớ dài hạn (khoảng cách ôn từ 21 ngày trở lên). */
    private long matureCards;

    /**
     * Trần số thẻ một phiên. Trả xuống để màn hình nói thẳng "hôm nay tối đa
     * 100 thẻ" thay vì để người học tự đoán vì sao còn 300 thẻ mà chỉ hiện 100.
     */
    private int dailyLimit;
}
