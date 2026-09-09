package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Một bài học đã được đánh dấu hoàn thành.
 *
 * Chỉ có mốc hoàn thành, KHÔNG có trạng thái "đang đọc dở": tiến độ là số bài
 * xong chia tổng số bài, đúng bằng thế. Thêm trạng thái trung gian sẽ kéo theo
 * câu hỏi "đọc tới đâu thì tính là dở" mà không ai trả lời được cho gọn.
 */
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
