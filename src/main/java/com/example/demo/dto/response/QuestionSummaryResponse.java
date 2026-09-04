package com.example.demo.dto.response;

import com.example.demo.domain.enums.QuestionType;
import com.example.demo.domain.model.Question;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Bản rút gọn của câu hỏi, dùng cho danh sách kết quả lọc/tìm kiếm.
 * <p>
 * Cố tình KHÔNG chứa {@code answers} và {@code explanation}: endpoint tìm kiếm mở cho cả học sinh,
 * trả kèm đáp án đúng ở đây là làm lộ đáp án. Chi tiết đầy đủ lấy qua GET /questions/{questionId}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionSummaryResponse {

    private Integer questionId;
    private String content;
    private QuestionType questionType;
    private Integer difficultyLevel;
    private Boolean isAiGenerated;
    private Integer bankId;
    private LocalDateTime createdAt;
    private List<TagResponse> tags;

    public static QuestionSummaryResponse from(Question question) {
        return QuestionSummaryResponse.builder()
                .questionId(question.getQuestionId())
                // getBank() là lazy proxy; đọc khoá chính không phát sinh query phụ
                .bankId(question.getBank() == null ? null : question.getBank().getBankId())
                .content(question.getContent())
                .questionType(question.getQuestionType())
                .difficultyLevel(question.getDifficultyLevel())
                .isAiGenerated(question.getIsAiGenerated())
                .createdAt(question.getCreatedAt())
                .tags(question.getTags() == null ? List.of() : question.getTags().stream()
                        .sorted(Comparator.comparing(tag -> tag.getTagName() == null ? "" : tag.getTagName()))
                        .map(tag -> TagResponse.builder()
                                .tagId(tag.getTagId())
                                .tagName(tag.getTagName())
                                .build())
                        .toList())
                .build();
    }
}
