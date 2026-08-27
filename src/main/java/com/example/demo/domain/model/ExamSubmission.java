package com.example.demo.domain.model;

import com.example.demo.domain.enums.SubmissionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "ExamSubmissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_submission_exam_student",
                columnNames = {"ExamID", "StudentID"})
)
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

    // VARCHAR thay vì ENUM riêng của MySQL (xem chú thích trong ExamQuestion)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "Status", length = 20)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.IN_PROGRESS;

    @Column(name = "AtRiskStatus")
    @Builder.Default
    private Boolean atRiskStatus = false;

    @Column(name = "SyncToGoogleClassroom")
    @Builder.Default
    private Boolean syncToGoogleClassroom = false;

    // ── Phiên làm bài ──────────────────────────────────────────────────────

    /**
     * Deadline của phiên thi, do server chốt một lần duy nhất lúc bắt đầu:
     * min(StartedAt + DurationMinutes, Exam.EndTime).
     *
     * Đây là mốc thời gian duy nhất có thẩm quyền. Client chỉ nhận cột này về
     * để đếm ngược; chỉnh đồng hồ máy không làm thay đổi thời gian còn lại.
     * Cột này KHÔNG được nới ra khi học sinh mất mạng — heartbeat chỉ dùng để
     * phát hiện rớt mạng, không dùng để bù giờ.
     */
    @Column(name = "ExpiresAt")
    private LocalDateTime expiresAt;

    /**
     * Lần cuối client còn liên lạc được với server (heartbeat 15-30 giây/lần).
     * Dùng để suy ra {@link #atRiskStatus}: học sinh im lặng quá lâu trong khi
     * phiên vẫn IN_PROGRESS thì rất có thể đã rớt mạng hoặc thoát đột ngột.
     */
    @Column(name = "LastActiveAt")
    private LocalDateTime lastActiveAt;

    /** Bài do server tự nộp khi hết giờ, không phải học sinh bấm nộp. */
    @Column(name = "AutoSubmitted")
    @Builder.Default
    private Boolean autoSubmitted = false;

    /** Phiên còn đang làm dở (chưa nộp). */
    public boolean isInProgress() {
        return status == SubmissionStatus.IN_PROGRESS;
    }

    /** Đã quá deadline chốt phía server chưa. Không có ExpiresAt thì coi như chưa. */
    public boolean isExpiredAt(LocalDateTime now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /** Số giây còn lại, tính theo giờ server. Không bao giờ trả về số âm. */
    public long remainingSeconds(LocalDateTime now) {
        if (expiresAt == null) {
            return 0L;
        }
        long seconds = java.time.Duration.between(now, expiresAt).getSeconds();
        return Math.max(seconds, 0L);
    }
}