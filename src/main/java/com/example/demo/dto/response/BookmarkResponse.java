package com.example.demo.dto.response;

import com.example.demo.domain.enums.JlptSkill;
import com.example.demo.domain.enums.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Một câu đã đánh dấu. Cố ý không mang đáp án đúng hay lời giải. */
@Data
@Builder
public class BookmarkResponse {
    private Integer bookmarkId;
    private Integer questionId;
    private String content;
    private QuestionType questionType;
    private JlptSkill skill;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
