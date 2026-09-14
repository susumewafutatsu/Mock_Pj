package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Một câu hỏi học viên chủ động đánh dấu, kèm ghi chú riêng. */
@Entity
@Table(name = "Bookmarks",
        uniqueConstraints = @UniqueConstraint(name = "uq_bookmark_user_question",
                columnNames = {"UserID", "QuestionID"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BookmarkID")
    private Integer bookmarkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "QuestionID", nullable = false)
    private Question question;

    @Column(name = "Note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "CreatedAt", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;
}
