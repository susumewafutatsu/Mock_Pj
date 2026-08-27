package com.example.demo.domain.model;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "Answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AnswerID")
    private Integer answerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "QuestionID", nullable = false)
    private Question question;

    // @Lob của Hibernate 6 map String thành CLOB dài 255 (tinytext trong MySQL),
    // trong khi changelog tạo LONGTEXT -> validate báo lệch kiểu.
    // LONGVARCHAR là kiểu ứng với LONGTEXT nên hai bên khớp nhau.
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "AnswerContent", nullable = false)
    private String answerContent;

    @Column(name = "IsCorrect", nullable = false)
    @Builder.Default
    private Boolean isCorrect = false;
}