package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một bộ thẻ trong danh sách, kèm tiến độ của chính người đang xem. */
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

    /** true = bộ dựng sẵn của hệ thống, không thuộc về người dùng nào. */
    private boolean systemDeck;

    private long totalCards;

    /** Số thẻ của bộ này mà người đang xem đã ghi danh học. */
    private long enrolledCards;

    /** Trong số đã ghi danh, bao nhiêu thẻ đã thuộc. */
    private long masteredCards;

    /** Đã bắt đầu học bộ này chưa (có ít nhất một thẻ được ghi danh). */
    private boolean enrolled;
}
