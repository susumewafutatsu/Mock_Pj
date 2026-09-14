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

    /** Đề công khai — ai cũng làm được, không cần vào phòng nào. */
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

    /** Số lượt làm bài tối đa cho mỗi thí sinh. */
    @Column(name = "MaxAttempts")
    private Integer maxAttempts;

    /** Cho thí sinh xem đáp án đúng + giải thích sau khi nộp hay không. */
    @Column(name = "AllowReview", nullable = false)
    @Builder.Default
    private Boolean allowReview = true;

    /** Chấm theo thang quy đổi JLPT (điểm từng nhóm 0–60/0–120, tổng 0–180, kết luận Đỗ/Trượt) thay vì điểm thô "5/5". */
    @Column(name = "JlptScoring", nullable = false)
    @Builder.Default
    private Boolean jlptScoring = false;

    /** Bài xếp trình độ: kết quả dùng để gợi ý học viên nên bắt đầu từ cấp nào. */
    @Column(name = "IsPlacement", nullable = false)
    @Builder.Default
    private Boolean isPlacement = false;

    /** Xáo thứ tự câu theo từng lượt làm. */
    @Column(name = "ShuffleQuestions", nullable = false)
    @Builder.Default
    private Boolean shuffleQuestions = false;

    /** Xáo thứ tự đáp án theo từng lượt làm — chống chép "câu 3 chọn C". */
    @Column(name = "ShuffleOptions", nullable = false)
    @Builder.Default
    private Boolean shuffleOptions = false;

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