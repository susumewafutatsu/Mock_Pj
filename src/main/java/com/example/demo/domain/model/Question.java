package com.example.demo.domain.model;

import com.example.demo.domain.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

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

    @Lob
    @Column(name = "Content", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "QuestionType", length = 20)
    @Builder.Default
    private QuestionType questionType = QuestionType.MULTIPLE_CHOICE;

    @Column(name = "DifficultyLevel")
    private Integer difficultyLevel;

    @Column(name = "IsAIGenerated")
    @Builder.Default
    private Boolean isAiGenerated = false;

    @Lob
    @Column(name = "Explanation")
    private String explanation;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

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