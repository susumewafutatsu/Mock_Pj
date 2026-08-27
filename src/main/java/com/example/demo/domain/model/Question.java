package com.example.demo.domain.model;

import com.example.demo.domain.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Câu hỏi "sống" trong ngân hàng câu hỏi — giáo viên được sửa tự do.
 * Các đề thi đã phát hành không đọc bảng này mà đọc snapshot trong
 * {@link ExamQuestion} / {@link ExamQuestionAnswer}.
 */
@Entity
@Table(name = "Questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QuestionID")
    private Integer questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BankID", nullable = false)
    private QuestionBank bank;

    // LONGVARCHAR khớp LONGTEXT của changelog (xem chú thích trong Answer)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "Content", nullable = false)
    private String content;

    // VARCHAR thay vì ENUM riêng của MySQL (xem chú thích trong ExamQuestion)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "QuestionType", length = 20)
    @Builder.Default
    private QuestionType questionType = QuestionType.MULTIPLE_CHOICE;

    @Column(name = "DifficultyLevel")
    private Integer difficultyLevel;

    @Column(name = "IsAIGenerated")
    @Builder.Default
    private Boolean isAiGenerated = false;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "Explanation")
    private String explanation;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    /** Xoá mềm: câu hỏi đã dùng trong đề thi không được xoá cứng. */
    @Column(name = "IsDeleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;
}