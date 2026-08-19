package com.example.demo.domain.model;

import lombok.*;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ExamQuestionKey implements Serializable {

    @Column(name = "ExamID")
    private Integer examId;

    @Column(name = "QuestionID")
    private Integer questionId;
}