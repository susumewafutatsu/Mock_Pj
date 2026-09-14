package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

/** Tỉ lệ đúng gộp theo một tag (≈ kỹ năng) của câu hỏi. */
@Data
@Builder
public class TagStatRow {

    private Integer tagId;
    private String tagName;

    private long answered;
    private long correct;

    /** Tỉ lệ đúng 0–100. null khi chưa có lượt nào. */
    private Integer correctPercent;
}
