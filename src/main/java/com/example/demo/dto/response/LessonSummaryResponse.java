package com.example.demo.dto.response;

import com.example.demo.domain.enums.LessonType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một bài trong danh sách bài của khoá. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonSummaryResponse {

    private Integer lessonId;

    private Integer orderNo;

    private String title;

    private LessonType lessonType;

    private Integer estimatedMinutes;

    /** Có bộ thẻ ôn kèm không. */
    private boolean hasDeck;

    /** Có bài kiểm tra cuối chặng không. */
    private boolean hasExam;

    /** Người đang xem đã qua chặng này chưa. */
    private boolean completed;

    /** Chặng còn khoá: có chặng đứng trước chưa qua. */
    private boolean locked;

    private Integer examId;
    private String examTitle;
    private Integer deckId;

    /** Ngưỡng qua chặng (%) khi chặng có bài kiểm tra. */
    private Integer minScorePercent;

    /** Điểm tốt nhất (%) của người đang xem ở bài kiểm tra của chặng; null = chưa làm. */
    private Double bestScorePercent;
}
