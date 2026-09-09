package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Một đề thi được đính kèm vào một phòng.
 *
 * Bảng nối này chính là thứ thay cho cột {@code Exams.ClassID} cũ. Đề không
 * còn "thuộc về" phòng nào: cùng một đề gắn được vào nhiều phòng, và gỡ khỏi
 * phòng không đụng gì tới đề.
 */
@Entity
@Table(name = "RoomExams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomExam {

    @EmbeddedId
    private RoomExamKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roomId")
    @JoinColumn(name = "RoomID")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("examId")
    @JoinColumn(name = "ExamID")
    private Exam exam;

    /** Thứ tự hiển thị trong phòng. */
    @Column(name = "OrderNo", nullable = false)
    @Builder.Default
    private Integer orderNo = 1;

    @CreationTimestamp
    @Column(name = "AttachedAt", updatable = false)
    private LocalDateTime attachedAt;
}
