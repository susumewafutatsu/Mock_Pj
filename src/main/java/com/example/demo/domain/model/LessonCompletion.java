package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Một bài học đã được đánh dấu hoàn thành. */
@Entity
@Table(name = "LessonCompletions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonCompletion {

    @EmbeddedId
    private LessonCompletionKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "UserID")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("lessonId")
    @JoinColumn(name = "LessonID")
    private CourseLesson lesson;

    @CreationTimestamp
    @Column(name = "CompletedAt", updatable = false)
    private LocalDateTime completedAt;
}
