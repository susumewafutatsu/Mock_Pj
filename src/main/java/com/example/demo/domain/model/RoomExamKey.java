package com.example.demo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/** Khoá của {@link RoomExam}: một đề chỉ gắn một lần vào một phòng. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RoomExamKey implements Serializable {

    @Column(name = "RoomID")
    private Integer roomId;

    @Column(name = "ExamID")
    private Integer examId;
}
