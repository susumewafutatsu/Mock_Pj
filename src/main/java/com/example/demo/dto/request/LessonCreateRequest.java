package com.example.demo.dto.request;

import com.example.demo.domain.enums.LessonType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tạo / sửa một bài học. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonCreateRequest {

    @NotBlank(message = "Tên bài học không được để trống")
    @Size(max = 200, message = "Tên bài học tối đa 200 ký tự")
    private String title;

    private LessonType lessonType;

    /** Lý thuyết để đọc. Một khối văn bản, không phải nhiều khối ghép. */
    private String content;

    private Integer estimatedMinutes;

    /** Bộ thẻ ôn kèm — để trống nếu bài này không cần thẻ nào. */
    private Integer deckId;

    /** Bài kiểm tra cuối bài — cũng không bắt buộc. */
    private Integer examId;

    /** Vị trí trong khoá. Để trống thì xếp xuống cuối. */
    private Integer orderNo;
}
