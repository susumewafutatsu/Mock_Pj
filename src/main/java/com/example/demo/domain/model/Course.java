package com.example.demo.domain.model;

import com.example.demo.domain.enums.CourseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Một khoá học — danh sách bài học lý thuyết có thứ tự. */
@Entity
@Table(name = "Courses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CourseID")
    private Integer courseId;

    @Column(name = "Title", nullable = false, length = 200)
    private String title;

    @Column(name = "Description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LevelID")
    private SubjectLevel level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AuthorID", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private CourseStatus status = CourseStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ReviewedBy")
    private User reviewedBy;

    @Column(name = "ReviewedAt")
    private LocalDateTime reviewedAt;

    /** Lý do từ chối, để tác giả biết phải sửa gì. */
    @Column(name = "ReviewNote", length = 500)
    private String reviewNote;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    // ── Hành vi ────────────────────────────────────────────────────────────

    public boolean isAuthoredBy(String userId) {
        return author != null && author.getUserId().equals(userId);
    }

    /** Thí sinh có thấy khoá này không. */
    public boolean isPublished() {
        return status == CourseStatus.PUBLISHED;
    }

    /** Gửi duyệt. Chỉ đi được từ DRAFT hoặc REJECTED. */
    public void submitForReview() {
        if (status != CourseStatus.DRAFT && status != CourseStatus.REJECTED) {
            throw new IllegalStateException("Khoá học đang ở trạng thái " + status);
        }
        status = CourseStatus.PENDING;
        reviewNote = null;
    }

    /** Admin duyệt. */
    public void approve(User admin, LocalDateTime now) {
        status = CourseStatus.PUBLISHED;
        reviewedBy = admin;
        reviewedAt = now;
        reviewNote = null;
    }

    /** Admin từ chối, kèm lý do — thiếu lý do thì tác giả không biết sửa gì. */
    public void reject(User admin, String note, LocalDateTime now) {
        status = CourseStatus.REJECTED;
        reviewedBy = admin;
        reviewedAt = now;
        reviewNote = note;
    }

    /** Đánh dấu nội dung vừa bị sửa. */
    public void markContentChanged() {
        if (status == CourseStatus.PUBLISHED) {
            status = CourseStatus.PENDING;
            reviewedBy = null;
            reviewedAt = null;
        }
    }
}
