package com.example.demo.dto.response;

import com.example.demo.domain.enums.StudyItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Một thẻ trong trang chi tiết bộ. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeckCardView {

    private StudyItemType itemType;
    private Integer itemId;
    private Integer orderNo;

    private String front;
    private String reading;
    private String back;
    private String example;
    private String exampleMeaning;
    private String note;

    /** NOT_ENROLLED | NEW | LEARNING | MATURE */
    private String status;
    private Integer intervalDays;
    private LocalDateTime dueAt;
    private boolean due;
    private Integer lapses;

    /** Sửa được nội dung (thẻ tự soạn trong bộ của mình). */
    private boolean editable;

    /** Gỡ được khỏi bộ (bộ của mình). */
    private boolean removable;
}
