package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một bộ thẻ kèm tiến độ của người đang xem. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeckResponse {

    private Integer deckId;
    private String name;
    private String description;
    private Integer levelId;
    private String levelName;

    /** Bộ dựng sẵn của hệ thống. */
    private boolean systemDeck;

    /** Bộ do chính người đang xem tạo. */
    private boolean mine;

    private long totalCards;

    /** Đã thêm bộ vào lịch học. */
    private boolean enrolled;

    /** Số thẻ của bộ đang có trong lịch. */
    private long enrolledCards;

    private long masteredCards;

    /** Thẻ ôn đến hạn của bộ. */
    private long dueCards;

    /** Thẻ mới chưa học của bộ. */
    private long newCards;
}
