package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Hàng đợi học thẻ: thẻ ôn đến hạn trước, thẻ mới sau. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewQueueResponse {

    /** Bộ đang học riêng; null = mọi bộ. */
    private Integer deckId;
    private String deckName;

    private List<ReviewCardResponse> cards;

    /** Thẻ ôn đến hạn (đã từng học). */
    private long reviewDue;

    /** Thẻ mới còn được mở hôm nay. */
    private long newAvailable;

    /** Tổng thẻ mới chưa học. */
    private long newWaiting;

    /** Thẻ mới đã học hôm nay (mọi bộ). */
    private long newStudiedToday;

    private int newPerDay;

    /** Cần học hôm nay = reviewDue + newAvailable. */
    private long dueCount;

    /** Số thẻ mới trong hàng đợi này. */
    private long newCount;

    private long totalCards;
    private long matureCards;

    /** Trần số thẻ ôn một phiên. */
    private int dailyLimit;
}
