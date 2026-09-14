
package com.example.demo.dto.response;

import com.example.demo.domain.enums.JlptSkill;
import com.example.demo.domain.enums.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Câu hỏi nhìn từ phía người ra đề (có cờ đáp án đúng). */
@Data
@Builder
public class QuestionResponse {
    private Integer questionId;
    private Integer bankId;
    private String content;
    private QuestionType questionType;
    private Integer difficultyLevel;

    /** Kỹ năng JLPT. null = chưa phân loại. */
    private JlptSkill skill;

    /** Bài đọc gắn với câu này (chỉ câu đọc hiểu). */
    private Integer passageId;
    private String passageTitle;

    /** File nghe (câu 聴解) và số lần được nghe. */
    private String audioUrl;
    private Integer maxAudioPlays;
    private String explanation;
    private boolean aiGenerated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AnswerResponse> answers;

    /** Câu hỏi đã được đưa vào đề thi nào chưa. */
    private boolean usedInExam;
}
