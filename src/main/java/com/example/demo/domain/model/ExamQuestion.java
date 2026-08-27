package com.example.demo.domain.model;

import com.example.demo.domain.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Liên kết đề thi - câu hỏi, đồng thời là bản đóng băng (snapshot) của câu hỏi
 * tại thời điểm được đưa vào đề.
 *
 * Đề thi KHÔNG đọc nội dung từ {@link Question} nữa. Nhờ vậy giáo viên sửa câu
 * hỏi trong ngân hàng bao nhiêu lần cũng không làm sai lệch bài đã nộp.
 * Quan hệ tới {@code question} chỉ còn để truy vết nguồn gốc và thống kê.
 */
@Entity
@Table(name = "ExamQuestions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamQuestion {

    @EmbeddedId
    private ExamQuestionKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("examId")
    @JoinColumn(name = "ExamID")
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("questionId")
    @JoinColumn(name = "QuestionID")
    private Question question;

    @Column(name = "QuestionOrder")
    private Integer questionOrder;

    @Column(name = "Points", precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal points = new BigDecimal("1.00");

    // ── Snapshot ───────────────────────────────────────────────────────────
    // Null với các dòng tạo trước migration v1.0.1; dùng resolveContent().

    // LONGVARCHAR khớp LONGTEXT của changelog (xem chú thích trong Answer)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "QuestionContent")
    private String questionContent;

    // Hibernate 6.4 map @Enumerated(STRING) sang kiểu ENUM riêng của MySQL,
    // còn changelog khai báo VARCHAR(20). Ép VARCHAR để validate không lệch.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "QuestionType", length = 20)
    private QuestionType questionType;

    @Column(name = "DifficultyLevel")
    private Integer difficultyLevel;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "Explanation")
    private String explanation;

    @Column(name = "SnapshotAt")
    private LocalDateTime snapshotAt;

    /** Sao chép trạng thái hiện tại của câu hỏi vào đề thi này. */
    public void captureFrom(Question source) {
        this.questionContent = source.getContent();
        this.questionType = source.getQuestionType();
        this.difficultyLevel = source.getDifficultyLevel();
        this.explanation = source.getExplanation();
        this.snapshotAt = LocalDateTime.now();
    }

    /** Nội dung hiển thị cho học sinh: ưu tiên snapshot, fallback câu hỏi gốc. */
    public String resolveContent() {
        return questionContent != null ? questionContent : question.getContent();
    }

    public QuestionType resolveType() {
        return questionType != null ? questionType : question.getQuestionType();
    }

    public boolean hasSnapshot() {
        return snapshotAt != null;
    }
}
