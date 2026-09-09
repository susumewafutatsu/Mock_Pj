package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "Exams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ExamID")
    private Integer examId;

    /**
     * Đề công khai — ai cũng làm được, không cần vào phòng nào.
     *
     * Thay cho quy ước ngầm cũ "ClassID IS NULL nghĩa là đề luyện tập tự do".
     * Quy ước đó biến mất cùng cột ClassID: quan hệ đề ↔ phòng giờ đi qua
     * {@link RoomExam}, nên một đề dùng lại được ở nhiều phòng và không còn
     * "thuộc về" chỗ nào cả.
     */
    @Column(name = "IsPublic", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LevelID")
    private SubjectLevel level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CreatedBy", nullable = false)
    private User createdBy;

    @Column(name = "Title", nullable = false, length = 200)
    private String title;

    @Column(name = "DurationMinutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "StartTime")
    private LocalDateTime startTime;

    @Column(name = "EndTime")
    private LocalDateTime endTime;

    @Column(name = "DriveBackupURL", length = 255)
    private String driveBackupUrl;

    @Column(name = "IsAdaptive")
    @Builder.Default
    private Boolean isAdaptive = false;

    /**
     * Số lượt làm bài tối đa cho mỗi thí sinh. {@code null} = không giới hạn.
     *
     * Đề luyện tập tự do thường để null — làm đi làm lại chính là mục đích của
     * nó. Đề giao trong phòng thi thường để 1, hoặc một con số nhỏ khi người ra đề muốn
     * thí sinh sửa sai rồi làm lại.
     *
     * Việc chặn KHÔNG nằm ở DB: ràng buộc UNIQUE trên ExamSubmissions chỉ chống
     * hai phiên trùng trong cùng một lượt. Đếm lượt và so với cột này là việc
     * của tầng service.
     */
    @Column(name = "MaxAttempts")
    private Integer maxAttempts;

    /**
     * Cho thí sinh xem đáp án đúng + giải thích sau khi nộp hay không.
     *
     * Bật (mặc định) thì trang xem lại bài hiện đầy đủ; tắt thì thí sinh chỉ
     * thấy điểm, câu nào đúng câu nào sai và bài làm của chính mình. Người ra đề
     * tắt khi đó là bài kiểm tra thật, hoặc khi đề còn đang mở cho phòng khác làm.
     */
    @Column(name = "AllowReview", nullable = false)
    @Builder.Default
    private Boolean allowReview = true;

    /** Không giới hạn số lượt làm bài. */
    public boolean isUnlimitedAttempts() {
        return maxAttempts == null || maxAttempts <= 0;
    }

    /** Thí sinh đã dùng {@code used} lượt thì còn được làm nữa không. */
    public boolean allowsAttempt(long used) {
        return isUnlimitedAttempts() || used < maxAttempts;
    }

    /** Số lượt còn lại, hoặc {@code null} khi đề không giới hạn. */
    public Integer attemptsRemaining(long used) {
        return isUnlimitedAttempts() ? null : (int) Math.max(maxAttempts - used, 0);
    }

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;
}