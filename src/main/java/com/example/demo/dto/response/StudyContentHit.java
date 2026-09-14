package com.example.demo.dto.response;

import com.example.demo.domain.enums.StudyItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một kết quả tìm từ vựng / chữ Hán có sẵn. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyContentHit {

    private StudyItemType itemType;
    private Integer itemId;
    private String front;
    private String reading;
    private String meaning;
    private String levelName;

    /** Đã có trong bộ đang soạn. */
    private boolean inDeck;
}
