package com.example.demo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/** Khoá của {@link CourseEnrollment}: một người ghi danh một khoá một lần. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CourseEnrollmentKey implements Serializable {

    @Column(name = "UserID", length = 50)
    private String userId;

    @Column(name = "CourseID")
    private Integer courseId;
}
