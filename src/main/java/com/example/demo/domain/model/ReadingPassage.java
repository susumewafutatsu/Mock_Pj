package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Đoạn văn của phần đọc hiểu (読解), dùng chung cho 2–4 câu hỏi. */
@Entity
@Table(name = "ReadingPassages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadingPassage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PassageID")
    private Integer passageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BankID", nullable = false)
    private QuestionBank bank;

    @Column(name = "Title", length = 200)
    private String title;

    // LONGVARCHAR khớp LONGTEXT của changelog (xem chú thích trong Answer)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "Content", nullable = false)
    private String content;

    @Column(name = "CreatedAt", updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
