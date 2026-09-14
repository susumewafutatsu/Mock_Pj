package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Một người đang theo một khoá học. */
@Entity
@Table(name = "CourseEnrollments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseEnrollment {

    @EmbeddedId
    private CourseEnrollmentKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "UserID")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("courseId")
    @JoinColumn(name = "CourseID")
    private Course course;

    @CreationTimestamp
    @Column(name = "StartedAt", updatable = false)
    private LocalDateTime startedAt;
}
