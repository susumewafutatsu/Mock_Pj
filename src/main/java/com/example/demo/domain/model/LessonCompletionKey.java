package com.example.demo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/** Khoá của {@link LessonCompletion}: một người hoàn thành một bài một lần. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LessonCompletionKey implements Serializable {

    @Column(name = "UserID", length = 50)
    private String userId;

    @Column(name = "LessonID")
    private Integer lessonId;
}
