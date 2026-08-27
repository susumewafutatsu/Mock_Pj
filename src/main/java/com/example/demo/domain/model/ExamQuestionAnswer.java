package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Bản đóng băng một đáp án của câu hỏi trong một đề thi cụ thể.
 *
 * Đây là nơi duy nhất quyết định "đáp án nào đúng" khi chấm bài của đề thi đó.
 * Giáo viên đổi đáp án đúng trong ngân hàng câu hỏi sẽ không hồi tố lên các đề
 * đã phát hành.
 */
@Entity
@Table(name = "ExamQuestionAnswers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamQuestionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SnapshotAnswerID")
    private Integer snapshotAnswerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "ExamID", referencedColumnName = "ExamID",
                        nullable = false, updatable = false),
            @JoinColumn(name = "QuestionID", referencedColumnName = "QuestionID",
                        nullable = false, updatable = false)
    })
    private ExamQuestion examQuestion;

    /** Đáp án gốc trong ngân hàng. Null nếu đáp án gốc đã bị xoá. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OriginalAnswerID")
    private Answer originalAnswer;

    // LONGVARCHAR khớp LONGTEXT của changelog (xem chú thích trong Answer)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "AnswerContent", nullable = false)
    private String answerContent;

    @Column(name = "IsCorrect", nullable = false)
    @Builder.Default
    private Boolean isCorrect = false;

    @Column(name = "AnswerOrder")
    private Integer answerOrder;
}
