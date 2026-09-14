package com.example.demo.dto.response;

import com.example.demo.domain.enums.ReviewGrade;
import com.example.demo.domain.enums.StudyItemType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/** Một thẻ trong phiên ôn tập. */
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

    // ── Riêng thẻ tự soạn ───────────────────────────────────────────────
    private String note;

    // ── Trạng thái ôn tập ───────────────────────────────────────────────
    /** Giữ tên JSON "isNew". */
    @JsonProperty("isNew")
    private boolean isNew;
    private Integer repetitions;
    private Integer intervalDays;
    private LocalDateTime dueAt;

    /** Số ngày sẽ gặp lại nếu chọn từng mức. */
    private Map<ReviewGrade, Integer> nextIntervals;
}
