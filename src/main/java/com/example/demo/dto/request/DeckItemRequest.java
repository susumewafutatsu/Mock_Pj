package com.example.demo.dto.request;

import com.example.demo.domain.enums.StudyItemType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Thêm từ vựng / chữ Hán có sẵn vào bộ. */
@Data
public class DeckItemRequest {

    @NotNull(message = "Thiếu loại thẻ")
    private StudyItemType itemType;

    @NotNull(message = "Thiếu thẻ")
    private Integer itemId;
}
