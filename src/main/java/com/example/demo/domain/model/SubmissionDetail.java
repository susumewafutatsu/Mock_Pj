package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "SubmissionDetails")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DetailID")
    private Integer detailId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SubmissionID", nullable = false)
    private ExamSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "QuestionID", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SelectedAnswerID")
    private Answer selectedAnswer;

    @Lob
    @Column(name = "EssayResponse")
    private String essayResponse;

    @Column(name = "IsCorrect")
    @Builder.Default
    private Boolean isCorrect = false;

    @Column(name = "ScoreEarned", precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal scoreEarned = new BigDecimal("0.00");

    @Lob
    @Column(name = "AIFeedback")
    private String aiFeedback;
}