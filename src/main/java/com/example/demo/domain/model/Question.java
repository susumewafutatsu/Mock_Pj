package com.example.demo.domain.model;

import com.example.demo.domain.enums.JlptSkill;
import com.example.demo.domain.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** Câu hỏi "sống" trong ngân hàng câu hỏi — người ra đề được sửa tự do. */
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

    /** Kỹ năng JLPT của câu — quyết định câu này được cộng vào ô nào trên bảng điểm quy đổi. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "Skill", length = 20)
    private JlptSkill skill;

    /** Đoạn văn mà câu này hỏi về. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PassageID")
    private ReadingPassage passage;

    /** File nghe của câu 聴解. Đường dẫn tương đối do máy chủ trả về khi tải file lên (xem MediaController) */
    @Column(name = "AudioUrl", length = 255)
    private String audioUrl;

    /** Số lần được nghe. NULL = 1, đúng như kỳ thi thật. */
    @Column(name = "MaxAudioPlays")
    private Integer maxAudioPlays;

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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "QuestionTags",
            joinColumns = @JoinColumn(name = "QuestionID"),
            inverseJoinColumns = @JoinColumn(name = "TagID")
    )
    // Nạp tag theo lô khi map cả trang kết quả, tránh N+1 query
    @BatchSize(size = 50)
    @Builder.Default
    private Set<Tag> tags = new LinkedHashSet<>();
}