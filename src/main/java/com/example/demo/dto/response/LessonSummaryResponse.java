package com.example.demo.dto.response;

import com.example.demo.domain.enums.LessonType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một bài trong danh sách bài của khoá.
 *
 * Cố ý KHÔNG có {@code content}: danh sách 30 bài mà kèm cả lý thuyết là một
 * response vài trăm KB cho một màn hình chỉ hiện tên bài. Nội dung lấy riêng khi
 * mở từng bài.
 */
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

    /** Có bài kiểm tra cuối bài không. */
    private boolean hasExam;

    /** Người đang xem đã hoàn thành bài này chưa. */
    private boolean completed;
}
