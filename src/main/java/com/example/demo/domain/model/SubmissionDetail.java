package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(
        name = "SubmissionDetails",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_detail_submission_question",
                columnNames = {"SubmissionID", "QuestionID"})
)
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
    @JoinColumn(name = "SelectedSnapshotAnswerID")
    private ExamQuestionAnswer selectedSnapshotAnswer;

  
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SelectedAnswerID")
    private Answer selectedAnswer;

    // LONGVARCHAR khớp LONGTEXT của changelog (xem chú thích trong Answer)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "EssayResponse")
    private String essayResponse;

    @Column(name = "IsCorrect")
    @Builder.Default
    private Boolean isCorrect = false;

    @Column(name = "ScoreEarned", precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal scoreEarned = new BigDecimal("0.00");

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "AIFeedback")
    private String aiFeedback;

    @Column(name = "AnsweredAt")
    private java.time.LocalDateTime answeredAt;
}