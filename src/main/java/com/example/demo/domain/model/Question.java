package com.example.demo.domain.model;

import com.example.demo.domain.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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
}