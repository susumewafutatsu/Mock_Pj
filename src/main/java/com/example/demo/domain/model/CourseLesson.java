package com.example.demo.domain.model;

import com.example.demo.domain.enums.LessonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Một bài học trong khoá — một khối lý thuyết để đọc. */
@Entity
@Table(name = "CourseLessons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseLesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LessonID")
    private Integer lessonId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CourseID", nullable = false)
    private Course course;

    @Column(name = "OrderNo", nullable = false)
    @Builder.Default
    private Integer orderNo = 1;

    @Column(name = "Title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "LessonType", nullable = false, length = 20)
    @Builder.Default
    private LessonType lessonType = LessonType.GRAMMAR;

    /** Lý thuyết để đọc. LONGVARCHAR khớp LONGTEXT của changelog. */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "Content")
    private String content;

    @Column(name = "EstimatedMinutes")
    private Integer estimatedMinutes;

    /** Bộ thẻ ôn kèm. Không bắt buộc. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DeckID")
    private Deck deck;

    /** Bài kiểm tra cuối chặng. Không bắt buộc. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ExamID")
    private Exam exam;

    /** Điểm tối thiểu (% của điểm tối đa) ở {@link #exam} để qua chặng. */
    @Column(name = "MinScorePercent")
    private Integer minScorePercent;

    public static final int DEFAULT_PASS_PERCENT = 60;

    /** Ngưỡng qua chặng thực tế. */
    public int passPercent() {
        return minScorePercent == null ? DEFAULT_PASS_PERCENT : minScorePercent;
    }

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;
}
