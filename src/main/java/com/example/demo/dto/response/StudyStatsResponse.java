package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Bảng "học hôm nay" — thứ hiện trên trang chủ của thí sinh. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyStatsResponse {

    /** Số câu sai chưa sửa được. */
    private long mistakesOpen;

    /** Trong đó, bao nhiêu câu đã tới hạn ôn. */
    private long mistakesDue;

    /** Số câu đã sửa được. */
    private long mistakesMastered;

    /** Tổng số thẻ đang học. */
    private long cardsTotal;

    /** Số thẻ đến hạn ôn hôm nay. */
    private long cardsDue;

    /** Số thẻ đã vào trí nhớ dài hạn. */
    private long cardsMature;
}
