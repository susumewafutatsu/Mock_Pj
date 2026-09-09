package com.example.demo.dto.response;

import com.example.demo.domain.enums.LessonType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Nội dung đầy đủ của một bài học — thứ thí sinh đọc. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonDetailResponse {

    private Integer lessonId;

    private Integer courseId;

    private String courseTitle;

    private Integer orderNo;

    private String title;

    private LessonType lessonType;

    private String content;

    private Integer estimatedMinutes;

    private Integer deckId;

    private String deckName;

    private Integer examId;

    private String examTitle;

    private boolean completed;

    /** Bài kế tiếp trong khoá, để đọc xong bấm đi tiếp. null nếu là bài cuối. */
    private Integer nextLessonId;

    /** Bài trước đó. null nếu là bài đầu. */
    private Integer previousLessonId;
}
