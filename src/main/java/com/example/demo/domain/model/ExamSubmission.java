package com.example.demo.domain.model;

import com.example.demo.domain.enums.SubmissionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ExamSubmissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SubmissionID")
    private Integer submissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ExamID", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StudentID", nullable = false)
    private User student;

    @CreationTimestamp
    @Column(name = "StartedAt", updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "SubmittedAt")
    private LocalDateTime submittedAt;

    @Column(name = "TotalScore", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal totalScore = new BigDecimal("0.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", length = 20)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.IN_PROGRESS;

    @Column(name = "AtRiskStatus")
    @Builder.Default
    private Boolean atRiskStatus = false;

    @Column(name = "SyncToGoogleClassroom")
    @Builder.Default
    private Boolean syncToGoogleClassroom = false;
}