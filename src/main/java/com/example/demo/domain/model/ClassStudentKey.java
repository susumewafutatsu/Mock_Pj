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
public class ClassStudentKey implements Serializable {

    @Column(name = "ClassID")
    private Integer classId;

    @Column(name = "StudentID", length = 50)
    private String studentId;
}