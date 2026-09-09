package com.example.demo.dto.response;

import com.example.demo.domain.enums.StudyItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Một thẻ trong phiên ôn tập.
 *
 * Một DTO dùng cho cả từ vựng lẫn chữ Hán, thay vì hai kiểu riêng: màn ôn thẻ
 * lật hết thẻ này tới thẻ khác trong cùng một hàng đợi trộn lẫn, nên hai kiểu
 * dữ liệu sẽ buộc client phải rẽ nhánh ở mọi chỗ. Trường nào không thuộc loại
 * thẻ hiện tại thì để null — {@link #itemType} cho biết phải đọc trường nào.
 *
 * {@link #prompt} là mặt trước của thẻ (chữ cần nhớ), phần còn lại là mặt sau.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCardResponse {

    private StudyItemType itemType;

    private Integer itemId;

    /** Mặt trước: từ vựng thì là chữ viết, chữ Hán thì là chính chữ đó. */
    private String prompt;

    /** Cách đọc bằng kana (từ vựng). */
    private String reading;

    private String meaning;

    // ── Riêng từ vựng ───────────────────────────────────────────────────
    private String partOfSpeech;
    private String exampleSentence;
    private String exampleMeaning;
    private String audioUrl;

    // ── Riêng chữ Hán ───────────────────────────────────────────────────
    private String onyomi;
    private String kunyomi;
    private Integer strokeCount;
    private String radical;
    private String mnemonic;

    // ── Trạng thái ôn tập, để màn hình nói được "thẻ mới" hay "ôn lại" ──
    private boolean isNew;
    private Integer repetitions;
    private Integer intervalDays;
    private LocalDateTime dueAt;
}
