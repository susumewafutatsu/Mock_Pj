package com.example.demo.dto.request;

import com.example.demo.domain.enums.LessonType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tạo / sửa một chặng của lộ trình ôn tập. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonCreateRequest {

    @NotBlank(message = "Tên chặng không được để trống")
    @Size(max = 200, message = "Tên chặng tối đa 200 ký tự")
    private String title;

    /** Điểm tối thiểu (%) ở bài kiểm tra của chặng để qua chặng. */
    private Integer minScorePercent;

    private LessonType lessonType;

    /** Lý thuyết để đọc. Một khối văn bản, không phải nhiều khối ghép. */
    private String content;

    private Integer estimatedMinutes;

    /** Bộ thẻ ôn kèm — để trống nếu bài này không cần thẻ nào. */
    private Integer deckId;

    /** Bài kiểm tra cuối chặng — không bắt buộc. */
    private Integer examId;

    /** Vị trí trong lộ trình. Để trống thì xếp xuống cuối. */
    private Integer orderNo;
}
