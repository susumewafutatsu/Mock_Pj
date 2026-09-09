package com.example.demo.domain.model;

import com.example.demo.domain.enums.LessonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Một bài học trong khoá — một khối lý thuyết để đọc.
 *
 * {@link #content} là MỘT khối văn bản, không phải nhiều khối ghép lại. Bản
 * thiết kế trước có sáu loại khối (text / ví dụ / audio / ảnh / danh sách từ /
 * quiz), kéo theo một trình soạn thảo khối — phần tốn công nhất của cả mảng học
 * tập, đổi lại một khoảng giá trị nhỏ. Một ô soạn thảo cho ra gần hết giá trị đó.
 *
 * {@link #deck} và {@link #exam} đều để trống được, và đó là chủ đích: bài ngữ
 * pháp thường chẳng cần thẻ nào, còn bài từ vựng thì gắn bộ thẻ để ôn lại sau
 * khi đọc. Bộ thẻ là công cụ ÔN, không phải thứ thay cho bài học.
 */
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

    /** Bài kiểm tra cuối bài. Không bắt buộc. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ExamID")
    private Exam exam;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;
}
