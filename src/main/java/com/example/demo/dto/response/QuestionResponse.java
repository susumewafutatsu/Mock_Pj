
package com.example.demo.dto.response;

import com.example.demo.domain.enums.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Câu hỏi nhìn từ phía giáo viên (có cờ đáp án đúng).
 * KHÔNG dùng DTO này cho học sinh đang làm bài.
 */
@Data
@Builder
public class QuestionResponse {
    private Integer questionId;
    private Integer bankId;
    private String content;
    private QuestionType questionType;
    private Integer difficultyLevel;
    private String explanation;
    private boolean aiGenerated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AnswerResponse> answers;

    /**
     * Câu hỏi đã được đưa vào đề thi nào chưa. Nếu có, lần sửa này chỉ ảnh
     * hưởng các đề tạo về sau — đề cũ giữ nguyên snapshot.
     */
    private boolean usedInExam;
}
