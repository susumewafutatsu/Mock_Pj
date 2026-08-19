package com.example.demo.domain.model;
import jakarta.persistence.*;
import lombok.*;

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

    @Lob
    @Column(name = "AnswerContent", nullable = false)
    private String answerContent;

    @Column(name = "IsCorrect", nullable = false)
    @Builder.Default
    private Boolean isCorrect = false;
}